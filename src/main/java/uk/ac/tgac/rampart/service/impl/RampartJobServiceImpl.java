/**
 * RAMPART - Robust Automatic MultiPle AssembleR Toolkit
 * Copyright (C) 2013  Daniel Mapleson - TGAC
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package uk.ac.tgac.rampart.service.impl;

import au.com.bytecode.opencsv.CSVReader;
import com.itextpdf.text.DocumentException;
import org.apache.velocity.VelocityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import uk.ac.tgac.conan.core.dao.AssemblyStatsDao;
import uk.ac.tgac.conan.core.dao.JobDao;
import uk.ac.tgac.conan.core.data.AssemblyStats;
import uk.ac.tgac.conan.core.data.Job;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.PlotFileStructure;
import uk.ac.tgac.conan.core.service.PdfOperationsService;
import uk.ac.tgac.conan.core.service.SequenceStatisticsService;
import uk.ac.tgac.rampart.data.RampartConfiguration;
import uk.ac.tgac.rampart.data.RampartJobFileStructure;
import uk.ac.tgac.rampart.data.RampartSettings;
import uk.ac.tgac.rampart.service.RampartJobService;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class RampartJobServiceImpl implements RampartJobService {
	
	private static Logger log = LoggerFactory.getLogger(RampartJobServiceImpl.class);
	
	// Services
	@Autowired private PdfOperationsService pdfOperationsService;
	@Autowired private SequenceStatisticsService sequenceStatisticsService;
	
	// DAOs
	@Autowired private JobDao jobDao;
	@Autowired private AssemblyStatsDao assemblyStatsDao;
	
	
	
	@Override
	public void seperatePlots(File in, File outDir, String filenamePrefix) throws IOException, DocumentException {		
		PlotFileStructure pfs = new PlotFileStructure(in, outDir, filenamePrefix);
		
		File plots = pfs.getPlots();
		
		this.pdfOperationsService.extractPage(plots, pfs.getNbContigsFile(), 1);
		this.pdfOperationsService.extractPage(plots, pfs.getNPcFile(), 6);
		this.pdfOperationsService.extractPage(plots, pfs.getNbBasesFile(), 7);
		this.pdfOperationsService.extractPage(plots, pfs.getAvgLenFile(), 10);
		this.pdfOperationsService.extractPage(plots, pfs.getMaxLenFile(), 9);
		this.pdfOperationsService.extractPage(plots, pfs.getN50File(), 11);
		
		try {
			this.pdfOperationsService.extractPage(plots, pfs.getScoreFile(), 12);
		}
		catch (IndexOutOfBoundsException e) {
			// Ignore this... might be that the PDF file doesn't contain a score page.
		}
	}

	
	protected List<String[]> getStats(File in) throws IOException {
		CSVReader reader = new CSVReader(new FileReader(in), '|');
		List<String[]> stats = reader.readAll();
		reader.close();
		return stats;
	}

	@Override
	public List<AssemblyStats> getAssemblyStats(File in) throws IOException {
		
		List<String[]> stats = getStats(in);
		List<AssemblyStats> asmStats = new ArrayList<AssemblyStats>();

		for(String[] rawStats : stats.subList(1, stats.size()-1)) {
            asmStats.add(new AssemblyStats(rawStats));
		}

		return asmStats;
	}

	@Override
	public AssemblyStats getWeightings(File in) throws IOException {
		List<String[]> stats = getStats(in);
		String[] rawStats = stats.get(1);
		AssemblyStats weightings = new AssemblyStats(rawStats);
		return weightings;		
	}
	
	@Override
	public VelocityContext buildContext(RampartJobFileStructure jobFS) throws IOException {

		// Get Configuration info from config files
		Job job = this.loadConfiguration(jobFS.getConfigFile()).getJob();
		List<Library> libsRaw = this.loadConfiguration(jobFS.getConfigRawFile()).getLibs();
		List<Library> libsQt = this.loadConfiguration(jobFS.getConfigQtFile()).getLibs();
		
		// Tool Settings from settings file
		RampartSettings rampartSettings = this.determineSettings(jobFS);
		
		// Analyse Read files and generate statistics for them.  Stats objects are already linked
		// to the seq files in the libs.
		calcReadStats(libsRaw);
		calcReadStats(libsQt);
		
		// Link libs to job object and vice versa
		job.setLibsRaw(libsRaw);
		job.setLibsQt(libsQt);
		
		// Get Info from Assemblies (MASS)
		List<AssemblyStats> massStats = this.getAssemblyStats(jobFS.getMassStatsFile());
		List<AssemblyStats> improverStats = this.getAssemblyStats(jobFS.getImproverStatsFile());
		//AssemblyStats weights = this.getWeightings(null);
		
		// Get Info from Best Assembly statistics
		AssemblyStats best = Collections.max(massStats);
		//best.setBest(true);
		
		// Link analyser to job object and vice versa
		//job.setMassStats(massStats);
		//job.setImproverStats(improverStats);

		// Get the final assembly statistics
		AssemblyStats finalAssembly = improverStats.get(improverStats.size() - 1);
		//finalAssembly.setFinalAssembly(Boolean.TRUE);
		
		// Getting the structure of this is important for both report building and data persistence
		// Build exectx
		VelocityContext vc = new VelocityContext();
		vc.put("job", job);
		vc.put("settings", rampartSettings);
		vc.put("contigScores", massStats);
		//vc.put("weightings", weights);
		vc.put("locations", jobFS);
		vc.put("best_mass_asm", best);
		vc.put("final_asm", finalAssembly);
		
		return vc;
	}
	

	protected void setJobInLibs(List<Library> libs, Job job) {
		for(Library l : libs) {
			l.setJob(job);
		}
	}

	
	@Override
	public void calcReadStats(List<Library> libs) throws IOException {
		
		// This can be long process so log it
		log.info("Starting analysis of input library files");
		
		StopWatch stopWatch = new StopWatch("Input Library Analysis");
		
		for (Library l : libs) {
			
			stopWatch.start(l.getName() + " : " + l.getFilePaired1().getFile().getName());
			this.sequenceStatisticsService.analyseReads(l.getFilePaired1());
			stopWatch.stop();
			
			stopWatch.start(l.getName() + " : " + l.getFilePaired2().getFile().getName());
			this.sequenceStatisticsService.analyseReads(l.getFilePaired2());
			stopWatch.stop();
			
			if (l.getSeFile() != null && !l.getSeFile().getFilePath().isEmpty()) {
				stopWatch.start(l.getName() + " : " + l.getSeFile().getFile().getName());
				this.sequenceStatisticsService.analyseReads(l.getSeFile());
				stopWatch.stop();
			}
		}
		
		log.info(stopWatch.prettyPrint());
	}
	
	
	@Override
	@Transactional
	public void persistContext(final VelocityContext vc) {
		
		Job j = (Job) vc.get("job");		
		jobDao.persist(j);
	}


	@Override
	public RampartConfiguration loadConfiguration(File in) throws IOException {
		
		RampartConfiguration rc = new RampartConfiguration();
        rc.load(in);
		return rc;
	}


	@Override
	public RampartSettings determineSettings(RampartJobFileStructure job) throws IOException {
		
		RampartSettings rampartSettings = new RampartSettings(job);
		
		rampartSettings.setRampartVersion(getClass().getPackage().getImplementationVersion());
		
		return rampartSettings;
	}

}