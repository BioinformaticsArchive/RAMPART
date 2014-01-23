package uk.ac.tgac.rampart.tool.process.analyse.asm;

import uk.ac.ebi.fgpt.conan.core.param.ArgValidator;
import uk.ac.ebi.fgpt.conan.core.param.ParameterBuilder;
import uk.ac.ebi.fgpt.conan.model.param.AbstractProcessParams;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.tgac.rampart.tool.process.analyse.asm.analysers.AssemblyAnalyser;
import uk.ac.tgac.rampart.util.SpiFactory;

/**
 * Created with IntelliJ IDEA.
 * User: maplesod
 * Date: 21/01/14
 * Time: 11:31
 * To change this template use File | Settings | File Templates.
 */
public class AnalyseAsmsParams extends AbstractProcessParams {

    private ConanParameter statsLevels;
    private ConanParameter massDir;
    private ConanParameter analyseReadsDir;
    private ConanParameter massGroups;
    private ConanParameter outputDir;
    private ConanParameter organism;
    private ConanParameter weightingsFile;
    private ConanParameter threadsPerProcess;
    private ConanParameter runParallel;
    private ConanParameter jobPrefix;

    public AnalyseAsmsParams() {

        this.statsLevels = new ParameterBuilder()
                .longName("statsLevels")
                .description("The type of assembly analysis to conduct.  Available options: " +
                        new SpiFactory<AssemblyAnalyser>(AssemblyAnalyser.class).listServicesAsString())
                .argValidator(ArgValidator.OFF)
                .create();

        this.massDir = new ParameterBuilder()
                .longName("massDir")
                .isOptional(false)
                .description("The location of the MASS output containing the assemblies to analyse")
                .argValidator(ArgValidator.PATH)
                .create();

        this.analyseReadsDir = new ParameterBuilder()
                .longName("analyseReadsDir")
                .description("The location of the reads analysis.  This is required if you are conducting a kmer analysis")
                .argValidator(ArgValidator.PATH)
                .create();

        this.massGroups = new ParameterBuilder()
                .longName("massGroups")
                .description("A comma separated list of the mass groups that should be analysed")
                .argValidator(ArgValidator.OFF)
                .create();

        this.outputDir = new ParameterBuilder()
                .longName("outputDir")
                .description("The location that output from the assembly analyser module should be placed")
                .argValidator(ArgValidator.PATH)
                .create();

        this.organism = new ParameterBuilder()
                .longName("organism")
                .description("A description of the organism's genome")
                .argValidator(ArgValidator.OFF)
                .create();

        this.weightingsFile = new ParameterBuilder()
                .longName("weightingsFile")
                .isOptional(false)
                .description("A file containing the weightings")
                .argValidator(ArgValidator.PATH)
                .create();

        this.threadsPerProcess = new ParameterBuilder()
                .longName("threads")
                .description("The number of threads to use for every child job.  Default: 1")
                .argValidator(ArgValidator.DIGITS)
                .create();

        this.runParallel = new ParameterBuilder()
                .longName("runParallel")
                .isFlag(true)
                .description("Whether to execute child jobs in parallel when possible")
                .argValidator(ArgValidator.OFF)
                .create();

        this.jobPrefix = new ParameterBuilder()
                .longName("jobPrefix")
                .description("The scheduler job name prefix to apply to each spawned child job")
                .argValidator(ArgValidator.DEFAULT)
                .create();
    }

    public ConanParameter getStatsLevels() {
        return statsLevels;
    }

    public ConanParameter getMassDir() {
        return massDir;
    }

    public ConanParameter getAnalyseReadsDir() {
        return analyseReadsDir;
    }

    public ConanParameter getMassGroups() {
        return massGroups;
    }

    public ConanParameter getOutputDir() {
        return outputDir;
    }

    public ConanParameter getOrganism() {
        return organism;
    }

    public ConanParameter getWeightingsFile() {
        return weightingsFile;
    }

    public ConanParameter getThreadsPerProcess() {
        return threadsPerProcess;
    }

    public ConanParameter getRunParallel() {
        return runParallel;
    }

    public ConanParameter getJobPrefix() {
        return jobPrefix;
    }

    @Override
    public ConanParameter[] getConanParametersAsArray() {
        return new ConanParameter[] {
                this.statsLevels,
                this.massDir,
                this.analyseReadsDir,
                this.massGroups,
                this.outputDir,
                this.organism,
                this.weightingsFile,
                this.threadsPerProcess,
                this.runParallel,
                this.jobPrefix
        };
    }
}
