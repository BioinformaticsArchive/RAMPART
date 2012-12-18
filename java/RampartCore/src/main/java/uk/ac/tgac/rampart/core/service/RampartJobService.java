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
package uk.ac.tgac.rampart.core.service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.velocity.VelocityContext;

import uk.ac.tgac.rampart.core.data.AssemblyStats;
import uk.ac.tgac.rampart.core.data.ImproverStats;
import uk.ac.tgac.rampart.core.data.Library;
import uk.ac.tgac.rampart.core.data.MassStats;
import uk.ac.tgac.rampart.core.data.RampartConfiguration;
import uk.ac.tgac.rampart.core.data.RampartJobFileStructure;
import uk.ac.tgac.rampart.core.data.RampartSettings;

import com.itextpdf.text.DocumentException;

public interface RampartJobService {

	void seperatePlots(File in, File outDir, String filenamePrefix) throws IOException, DocumentException;
	
	RampartConfiguration loadConfiguration(File in) throws IOException;
	
	List<MassStats> getMassStats(File in) throws IOException;
	
	List<ImproverStats> getImproverStats(File in) throws IOException;
	
	AssemblyStats getWeightings(File in) throws IOException;
	
	void calcReadStats(List<Library> libs) throws IOException;
	
	VelocityContext buildContext(File jobDir, File rampartDir) throws IOException;
	
	RampartSettings determineSettings(RampartJobFileStructure job) throws IOException;
	
	void persistContext(final VelocityContext context);
}
