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
package uk.ac.tgac.rampart.tool.process.amp;

import org.w3c.dom.Element;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.param.ProcessArgs;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.Organism;
import uk.ac.tgac.conan.core.util.XmlHelper;
import uk.ac.tgac.rampart.tool.process.mecq.EcqArgs;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * User: maplesod
 * Date: 16/08/13
 * Time: 15:05
 */
public class AmpStageArgs implements ProcessArgs {

    private static final String KEY_ELEM_TOOL = "tool";
    private static final String KEY_ATTR_THREADS = "threads";
    private static final String KEY_ATTR_MEMORY = "memory";
    private static final String KEY_ELEM_OTHER_ARGS = "args";


    // Defaults
    public static final int DEFAULT_THREADS = 1;
    public static final int DEFAULT_MEMORY = 0;
    public static final String DEFAULT_OTHER_ARGS = "";


    // Common stuff
    private File outputDir;
    private File assembliesDir;
    private String jobPrefix;
    private List<Library> allLibraries;
    private List<EcqArgs> allMecqs;
    private Organism organism;

    // Specifics
    private String tool;
    private File input;
    private int index;
    private int threads;
    private int memory;
    private String otherArgs;


    public AmpStageArgs() {

        this.tool = "";
        this.input = null;
        this.index = 0;
        this.threads = DEFAULT_THREADS;
        this.memory = DEFAULT_MEMORY;
        this.otherArgs = DEFAULT_OTHER_ARGS;

        this.outputDir = new File("");
        this.jobPrefix = "AMP-" + this.index;
        this.allLibraries = null;
        this.allMecqs = null;
        this.organism = null;
    }

    public AmpStageArgs(Element ele, File outputDir, File assembliesDir, String jobPrefix, List<Library> allLibraries,
                        List<EcqArgs> allMecqs, Organism organism, File input, int index) {

        // Set defaults
        this();

        // Requires
        this.tool = XmlHelper.getTextValue(ele, KEY_ELEM_TOOL);

        // Optional
        this.threads = ele.hasAttribute(KEY_ATTR_THREADS) ? XmlHelper.getIntValue(ele, KEY_ATTR_THREADS) : DEFAULT_THREADS;
        this.memory = ele.hasAttribute(KEY_ATTR_MEMORY) ? XmlHelper.getIntValue(ele, KEY_ATTR_MEMORY) : DEFAULT_MEMORY;
        this.otherArgs = ele.hasAttribute(KEY_ELEM_OTHER_ARGS) ? XmlHelper.getTextValue(ele, KEY_ELEM_OTHER_ARGS) : DEFAULT_OTHER_ARGS;

        // Other args
        this.outputDir = outputDir;
        this.assembliesDir = assembliesDir;
        this.jobPrefix = jobPrefix;
        this.allLibraries = allLibraries;
        this.allMecqs = allMecqs;
        this.organism = organism;
        this.input = input;
        this.index = index;
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

    public List<Library> getAllLibraries() {
        return allLibraries;
    }

    public void setAllLibraries(List<Library> allLibraries) {
        this.allLibraries = allLibraries;
    }

    public List<EcqArgs> getAllMecqs() {
        return allMecqs;
    }

    public void setAllMecqs(List<EcqArgs> allMecqs) {
        this.allMecqs = allMecqs;
    }

    public Organism getOrganism() {
        return organism;
    }

    public void setOrganism(Organism organism) {
        this.organism = organism;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public File getInput() {
        return input;
    }

    public void setInput(File input) {
        this.input = input;
    }

    public File getOutputFile() {
        return new File(this.outputDir, "amp-" + this.index + ".fa");
    }


    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public File getAssembliesDir() {
        return assembliesDir;
    }

    public void setAssembliesDir(File assembliesDir) {
        this.assembliesDir = assembliesDir;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getMemory() {
        return memory;
    }

    public void setMemory(int memory) {
        this.memory = memory;
    }

    public String getOtherArgs() {
        return otherArgs;
    }

    public void setOtherArgs(String otherArgs) {
        this.otherArgs = otherArgs;
    }

    @Override
    public void parse(String args) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Map<ConanParameter, String> getArgMap() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void setFromArgMap(Map<ConanParameter, String> pvp) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}