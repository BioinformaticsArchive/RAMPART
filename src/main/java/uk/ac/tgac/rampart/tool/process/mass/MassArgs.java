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
package uk.ac.tgac.rampart.tool.process.mass;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.param.ProcessArgs;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.Organism;
import uk.ac.tgac.conan.core.util.XmlHelper;
import uk.ac.tgac.rampart.tool.process.mass.single.SingleMassArgs;
import uk.ac.tgac.rampart.tool.process.mass.single.SingleMassParams;
import uk.ac.tgac.rampart.tool.process.mecq.MecqSingleArgs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: maplesod
 * Date: 10/01/13
 * Time: 17:04
 */
public class MassArgs implements ProcessArgs {

    // Keys for config file
    public static final String KEY_ATTR_PARALLEL = "parallel";
    public static final String KEY_ELEM_SINGLE_MASS = "single_mass";

    // Constants
    public static final int DEFAULT_CVG_CUTOFF = -1;
    public static final OutputLevel DEFAULT_OUTPUT_LEVEL = OutputLevel.CONTIGS;


    // Need access to these
    private SingleMassParams params = new SingleMassParams();

    // Rampart vars
    private String jobPrefix;
    private File outputDir;
    List<SingleMassArgs> singleMassArgsList;    // List of Single MASS groups to run separately
    List<Library> allLibraries;                    // All allLibraries available in this job
    List<MecqSingleArgs> allMecqs;                 // All mecq configurations
    private boolean runParallel;                // Whether to run MASS groups in parallel
    private File weightings;
    private Organism organism;


    private OutputLevel outputLevel;


    public enum OutputLevel {
        CONTIGS,
        SCAFFOLDS
    }

    public MassArgs() {
        this.jobPrefix = "";
        this.outputDir = null;

        this.allLibraries = new ArrayList<Library>();
        this.allMecqs = new ArrayList<MecqSingleArgs>();

        this.outputLevel = DEFAULT_OUTPUT_LEVEL;
        this.weightings = new File(
                new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile(),
                "/data/weightings.tab");

        this.organism = null;
    }

    public MassArgs(Element ele, File outputDir, String jobPrefix, List<Library> allLibraries, List<MecqSingleArgs> allMecqs, Organism organism) {

        // Set defaults first
        this();

        // Set from parameters
        this.outputDir = outputDir;
        this.jobPrefix = jobPrefix;
        this.allLibraries = allLibraries;
        this.allMecqs = allMecqs;
        this.organism = organism;

        this.runParallel = XmlHelper.getBooleanValue(ele, KEY_ATTR_PARALLEL);

        // All single mass args
        NodeList nodes = ele.getElementsByTagName(KEY_ELEM_SINGLE_MASS);
        for(int i = 0; i < nodes.getLength(); i++) {
            this.singleMassArgsList.add(
                    new SingleMassArgs(
                            (Element)nodes.item(i), outputDir, jobPrefix,
                            this.allLibraries, this.allMecqs, this.organism));
        }
    }


    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    public String getJobPrefix() {
        return jobPrefix;
    }

    public void setJobPrefix(String jobPrefix) {
        this.jobPrefix = jobPrefix;
    }

    public OutputLevel getOutputLevel() {
        return outputLevel;
    }

    public void setOutputLevel(OutputLevel outputLevel) {
        this.outputLevel = outputLevel;
    }

    public boolean isRunParallel() {
        return runParallel;
    }

    public void setRunParallel(boolean runParallel) {
        this.runParallel = runParallel;
    }

    public File getWeightings() {
        return weightings;
    }

    public void setWeightings(File weightings) {
        this.weightings = weightings;
    }

    public Organism getOrganism() {
        return organism;
    }

    public void setOrganism(Organism organism) {
        this.organism = organism;
    }

    public List<Library> getAllLibraries() {
        return allLibraries;
    }

    public void setAllLibraries(List<Library> allLibraries) {
        this.allLibraries = allLibraries;
    }

    public List<SingleMassArgs> getSingleMassArgsList() {
        return singleMassArgsList;
    }

    public void setSingleMassArgsList(List<SingleMassArgs> singleMassArgsList) {
        this.singleMassArgsList = singleMassArgsList;
    }

    public List<MecqSingleArgs> getAllMecqs() {
        return allMecqs;
    }

    public void setAllMecqs(List<MecqSingleArgs> allMecqs) {
        this.allMecqs = allMecqs;
    }

    @Override
    public void parse(String args) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Map<ConanParameter, String> getArgMap() {

        Map<ConanParameter, String> pvp = new HashMap<ConanParameter, String>();


        if (this.outputLevel != null) {
            pvp.put(params.getOutputLevel(), this.outputLevel.toString());
        }

        if (this.outputDir != null)
            pvp.put(params.getOutputDir(), this.outputDir.getAbsolutePath());

        if (this.jobPrefix != null)
            pvp.put(params.getJobPrefix(), this.jobPrefix);


        return pvp;
    }

    @Override
    public void setFromArgMap(Map<ConanParameter, String> pvp) {
        for (Map.Entry<ConanParameter, String> entry : pvp.entrySet()) {

            if (!entry.getKey().validateParameterValue(entry.getValue())) {
                throw new IllegalArgumentException("Parameter invalid: " + entry.getKey() + " : " + entry.getValue());
            }

            String param = entry.getKey().getName();

            if (param.equals(this.params.getJobPrefix().getName())) {
                this.jobPrefix = entry.getValue();
            } else if (param.equals(this.params.getOutputDir().getName())) {
                this.outputDir = new File(entry.getValue());
            } else if (param.equalsIgnoreCase(this.params.getOutputLevel().getName())) {
                this.outputLevel = OutputLevel.valueOf(entry.getValue());
            }
        }
    }



}
