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
package uk.ac.tgac.rampart.tool.process.mecq;

import org.w3c.dom.Element;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.util.XmlHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * User: maplesod
 * Date: 14/08/13
 * Time: 17:21
 */
public class MecqSingleArgs {

    // **** Xml Config file property keys ****

    public static final String KEY_ELEM_TOOL = "tool";
    public static final String KEY_ELEM_MIN_LEN = "minlen";
    public static final String KEY_ELEM_MIN_QUAL = "minqual";
    public static final String KEY_ELEM_KMER = "kmer";
    public static final String KEY_ELEM_LIBS = "libs";

    public static final String KEY_ATTR_NAME = "name";
    public static final String KEY_ATTR_THREADS = "threads";
    public static final String KEY_ATTR_MEMORY = "memory";


    // **** Default values ****

    public static final int DEFAULT_MIN_LEN = 60;
    public static final int DEFAULT_MIN_QUAL = 30;
    public static final int DEFAULT_KMER = 17;
    public static final int DEFAULT_THREADS = 8;
    public static final int DEFUALT_MEMORY = 20;

    public static final String RAW = "raw";


    // **** Class vars ****

    private String name;
    private String tool;
    private int minLen;
    private int minQual;
    private int kmer;
    private int threads;
    private int memory;
    private List<Library> libraries;
    private File outputDir;
    private String jobPrefix;

    public MecqSingleArgs() {
        this.name = "";
        this.minLen = DEFAULT_MIN_LEN;
        this.minQual = DEFAULT_MIN_QUAL;
        this.kmer = DEFAULT_KMER;
        this.threads = DEFAULT_THREADS;
        this.memory = DEFUALT_MEMORY;
        this.libraries = new ArrayList<Library>();
    }


    public MecqSingleArgs(Element ele, List<Library> allLibraries, File parentOutputDir, String parentJobPrefix) {

        // Set defaults
        this();

        this.name = XmlHelper.getTextValue(ele, KEY_ATTR_NAME);
        this.tool = XmlHelper.getTextValue(ele, KEY_ELEM_TOOL);
        this.minLen = XmlHelper.getIntValue(ele, KEY_ELEM_MIN_LEN);
        this.minQual = XmlHelper.getIntValue(ele, KEY_ELEM_MIN_QUAL);
        this.kmer = XmlHelper.getIntValue(ele, KEY_ELEM_KMER);
        this.threads = XmlHelper.getIntValue(ele, KEY_ATTR_THREADS);
        this.memory = XmlHelper.getIntValue(ele, KEY_ATTR_MEMORY);

        // Filter the provided libs
        String libList = XmlHelper.getTextValue(ele, KEY_ELEM_LIBS);
        String[] libIds = libList.split(",");

        for(String libId : libIds) {
            for(Library lib : allLibraries) {
                if (lib.getName().equalsIgnoreCase(libId.trim())) {
                    this.libraries.add(lib);
                    break;
                }
            }
        }

        this.outputDir = new File(parentOutputDir, name);
        this.jobPrefix = parentJobPrefix + "-name";
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public int getMinLen() {
        return minLen;
    }

    public void setMinLen(int minLen) {
        this.minLen = minLen;
    }

    public int getMinQual() {
        return minQual;
    }

    public void setMinQual(int minQual) {
        this.minQual = minQual;
    }

    public int getKmer() {
        return kmer;
    }

    public void setKmer(int kmer) {
        this.kmer = kmer;
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

    public List<Library> getLibraries() {
        return libraries;
    }

    public void setLibraries(List<Library> libraries) {
        this.libraries = libraries;
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

    public List<File> getOutputFiles(String libName) {
        return new ArrayList<File>();
    }
}
