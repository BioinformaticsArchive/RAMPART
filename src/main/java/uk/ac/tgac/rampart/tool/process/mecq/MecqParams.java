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

import uk.ac.ebi.fgpt.conan.core.param.*;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.param.ProcessParams;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: maplesod
 * Date: 16/01/13
 * Time: 13:37
 */
public class MecqParams implements ProcessParams {

    private ConanParameter rampartConfig;
    private ConanParameter tool;
    private ConanParameter libs;
    private ConanParameter outputDir;
    private ConanParameter minLength;
    private ConanParameter minQuality;
    private ConanParameter kmer;
    private ConanParameter threads;
    private ConanParameter memoryGb;
    private ConanParameter createConfigs;
    private ConanParameter jobPrefix;
    private ConanParameter runParallel;
    private ConanParameter noQT;

    public MecqParams() {

        this.rampartConfig = new PathParameter(
                "qtConfig",
                "The rampart configuration file describing the libraries to quality trim",
                true);

        this.tool = new ParameterBuilder()
                .longName("tool")
                .description("The quality trimming tool to be used")
                .isOptional(false)
                .create();

        this.libs = new ParameterBuilder()
                .longName("libs")
                .description("The libraries to be quality trimmed")
                .isOptional(false)
                .argValidator(ArgValidator.OFF)
                .create();

        this.outputDir = new PathParameter(
                "qtOutput",
                "The directory to place the quality trimmed libraries",
                false);

        this.minLength = new NumericParameter(
                "minLength",
                "The minimum length for trimmed reads.  Any reads shorter than this value after trimming are discarded",
                true);

        this.minQuality = new NumericParameter(
                "minQuality",
                "The minimum quality for trimmed reads.  Any reads with quality scores lower than this value will be trimmed.",
                true);

        this.kmer = new NumericParameter(
                "kmer",
                "The kmer value to use for Kmer Frequency Spectrum based correction.  This is often a different value to that used in genome assembly.  See individual correction tool for details.  Default: 17",
                true);

        this.threads = new NumericParameter(
                "threads",
                "The number of threads to use to process data.  Default: 8",
                true);

        this.memoryGb = new NumericParameter(
                "memory_gb",
                "A figure used as a guide to run process in an efficient manner.  Normally it is sensible to slightly overestimate requirements if resources allow it.  Default: 20GB",
                true);

        this.createConfigs = new FlagParameter(
                "createConfigs",
                "Whether or not to create separate RAMPART configuration files for RAW and QT datasets in the output directory");

        this.jobPrefix = new ParameterBuilder()
                .longName("jobPrefix")
                .description("If using a scheduler this prefix is applied to the job names of all child QT processes")
                .create();

        this.runParallel = new FlagParameter(
                "runParallel",
                "If set to true, and we want to run QT in a scheduled execution context, then each library provided to this " +
                        "QT process will be executed in parallel.  A wait job will be executed in the foreground which will " +
                        "complete after all libraries have been quality trimmed");

        this.noQT = new FlagParameter(
                "noQT",
                "If set to true then we don't actually do any Quality trimming.  We still do everything else though, which " +
                        "includes creating the output directory and the RAW configuration file and symbolic links");
    }

    public ConanParameter getRampartConfig() {
        return rampartConfig;
    }

    public ConanParameter getTool() {
        return tool;
    }

    public ConanParameter getLibs() {
        return libs;
    }

    public ConanParameter getMinLength() {
        return minLength;
    }

    public ConanParameter getMinQuality() {
        return minQuality;
    }

    public ConanParameter getKmer() {
        return kmer;
    }

    public ConanParameter getThreads() {
        return threads;
    }

    public ConanParameter getMemoryGb() {
        return memoryGb;
    }

    public ConanParameter getNoQT() {
        return noQT;
    }

    public ConanParameter getOutputDir() {
        return outputDir;
    }

    public ConanParameter getCreateConfigs() {
        return createConfigs;
    }

    public ConanParameter getJobPrefix() {
        return jobPrefix;
    }

    public ConanParameter getRunParallel() {
        return runParallel;
    }

    @Override
    public List<ConanParameter> getConanParameters() {
        return new ArrayList<>(Arrays.asList(
                new ConanParameter[]{
                        this.rampartConfig,
                        this.tool,
                        this.libs,
                        this.minLength,
                        this.minQuality,
                        this.kmer,
                        this.threads,
                        this.memoryGb,
                        this.noQT,
                        this.outputDir,
                        this.createConfigs,
                        this.jobPrefix,
                        this.runParallel
                }));
    }
}
