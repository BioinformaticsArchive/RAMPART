/*
 * RAMPART - Robust Automatic MultiPle AssembleR Toolkit
 * Copyright (C) 2015  Daniel Mapleson - TGAC
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
 */
package uk.ac.tgac.rampart.stage;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.ac.ebi.fgpt.conan.core.context.DefaultExecutionContext;
import uk.ac.ebi.fgpt.conan.core.context.DefaultExecutionResult;
import uk.ac.ebi.fgpt.conan.core.context.locality.Local;
import uk.ac.ebi.fgpt.conan.core.param.ArgValidator;
import uk.ac.ebi.fgpt.conan.core.param.DefaultParamMap;
import uk.ac.ebi.fgpt.conan.core.param.ParameterBuilder;
import uk.ac.ebi.fgpt.conan.core.param.PathParameter;
import uk.ac.ebi.fgpt.conan.core.pipeline.AbstractConanPipeline;
import uk.ac.ebi.fgpt.conan.core.process.AbstractConanProcess;
import uk.ac.ebi.fgpt.conan.core.process.AbstractProcessArgs;
import uk.ac.ebi.fgpt.conan.core.user.GuestUser;
import uk.ac.ebi.fgpt.conan.factory.DefaultTaskFactory;
import uk.ac.ebi.fgpt.conan.model.ConanProcess;
import uk.ac.ebi.fgpt.conan.model.ConanTask;
import uk.ac.ebi.fgpt.conan.model.ConanUser;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionContext;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionResult;
import uk.ac.ebi.fgpt.conan.model.context.ResourceUsage;
import uk.ac.ebi.fgpt.conan.model.context.TaskResult;
import uk.ac.ebi.fgpt.conan.model.param.AbstractProcessParams;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.param.ParamMap;
import uk.ac.ebi.fgpt.conan.service.ConanExecutorService;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.ebi.fgpt.conan.service.exception.TaskExecutionException;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.Organism;
import uk.ac.tgac.conan.core.util.XmlHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class wraps a Pipeline to manage each AMP stage
 *
 * User: maplesod
 * Date: 12/02/13
 * Time: 16:30
 */
public class Amp extends AbstractConanProcess {

    private static Logger log = LoggerFactory.getLogger(Amp.class);

    public Amp() {
        this(null);
    }

    public Amp(ConanExecutorService ces) {
        this(ces, new Args());
    }

    public Amp(ConanExecutorService ces, Args args) {
        super("", args, new Params(), ces);
    }

    public Args getArgs() {
        return (Args)this.getProcessArgs();
    }

    @Override
    public String getName() {
        return "AMP (Assembly iMProver)";
    }

    @Override
    public boolean isOperational(ExecutionContext executionContext) {

        // Create AMP Pipeline
        Pipeline ampPipeline = new Pipeline(this.conanExecutorService, this.getArgs());

        if (!ampPipeline.isOperational(executionContext)) {
            log.warn("AMP stage is NOT operational.");
            return false;
        }

        log.info("AMP stage is operational.");
        return true;
    }

    @Override
    public String getCommand() {
        return null;
    }

    @Override
    public ExecutionResult execute(ExecutionContext executionContext) throws InterruptedException, ProcessExecutionException {

        // Short cut to arguments
        Args args = this.getArgs();

        try {

            // Create AMP Pipeline
            Pipeline ampPipeline = new Pipeline(this.conanExecutorService, args);

            log.debug("Found " + ampPipeline.getProcesses().size() + " AMP stages in pipeline to process");

            // Clear out anything that was here before
            if (args.getOutputDir().exists()) {
                FileUtils.deleteDirectory(args.getOutputDir());
            }

            // Make sure the output directory exists
            args.getAssembliesDir().mkdirs();

            // Create link for the initial input file
            this.getConanProcessService().createLocalSymbolicLink(
                    args.getInputAssembly(),
                    new File(args.getAssembliesDir(), "amp-stage-0.fa"));

            // Create a guest user
            ConanUser rampartUser = new GuestUser("daniel.mapleson@tgac.ac.uk");

            // Create the AMP task
            ConanTask<Pipeline> ampTask = new DefaultTaskFactory().createTask(
                    ampPipeline,
                    0,
                    ampPipeline.getArgs().getArgMap(),
                    ConanTask.Priority.HIGHEST,
                    rampartUser);

            ampTask.setId("AMP");
            ampTask.submit();

            // Run the AMP pipeline
            TaskResult result;
            try {
                result = ampTask.execute(executionContext);
            } catch (TaskExecutionException e) {
                throw new ProcessExecutionException(-1, e);
            }

            // Create a symbolic link for the final assembly from the final stage
            this.getConanProcessService().createLocalSymbolicLink(
                    new File(args.getAssembliesDir(), "amp-stage-" + ampPipeline.getProcesses().size() + ".fa"),
                    args.getFinalAssembly());

            // Output the resource usage to file
            FileUtils.writeLines(new File(args.getOutputDir(), args.getJobPrefix() + ".summary"), result.getOutput());

            return new DefaultExecutionResult(
                    "rampart-amp",
                    0,
                    result.getOutput().toArray(new String[result.getOutput().size()]),
                    null,
                    -1,
                    new ResourceUsage(result.getMaxMemUsage(), result.getActualTotalRuntime(), result.getTotalExternalCputime()));
        }
        catch(IOException e) {
            throw new ProcessExecutionException(-1, e);
        }
    }

    public static class Pipeline extends AbstractConanPipeline {

        private static final String NAME = "AMP";
        private static final ConanUser USER = new GuestUser("rampart@tgac.ac.uk");


        private Params params = new Params();
        private ConanExecutorService conanExecutorService;

        private Args args;

        public Pipeline(ConanExecutorService ces, Args ampArgs) {

            super(NAME, USER, false, false, ces.getConanProcessService());

            this.conanExecutorService = ces;
            this.args = ampArgs;
            this.args.setExecutionContext(ces.getExecutionContext());

            this.init();
        }

        public Args getArgs() {
            return args;
        }

        public void setArgs(Args args) {
            this.args = args;

            this.init();
        }


        public void init() {

            for(AmpStage.Args ampStageArgs : this.args.getStageArgsList()) {

                AmpStage proc = new AmpStage(this.conanExecutorService, ampStageArgs);
                proc.setConanProcessService(getConanProcessService());
                this.addProcess(proc);
            }

            // Check all processes in the pipeline are operational, modify execution context to execute unscheduled locally
            if (!this.isOperational(new DefaultExecutionContext(new Local(), null,
                    this.args.getExecutionContext().getExternalProcessConfiguration()))) {
                throw new UnsupportedOperationException("AMP pipeline contains one or more processes that are not currently operational.  " +
                        "Please fix before restarting pipeline.");
            }
        }

    }


    public static class Args extends AbstractProcessArgs implements RampartStageArgs {

        private static final String INPUT_ASSEMBLY = "input";
        private static final String KEY_ELEM_AMP_STAGE = "stage";

        private File inputAssembly;
        private File bubbleFile;
        private File outputDir;
        private List<Library> allLibraries;
        private List<Mecq.EcqArgs> allMecqs;
        private List<AmpStage.Args> stageArgsList;
        private String jobPrefix;
        private Organism organism;
        private ExecutionContext executionContext;


        public Args() {
            super(new Params());
            this.inputAssembly = null;
            this.bubbleFile = null;
            this.outputDir = null;
            this.allLibraries = new ArrayList<>();
            this.allMecqs = new ArrayList<>();
            this.stageArgsList = new ArrayList<>();
            this.jobPrefix = "amp";
        }

        public Args(Element ele, File outputDir, String jobPrefix, File inputAssembly, File bubbleFile,
                       List<Library> allLibraries, List<Mecq.EcqArgs> allMecqs, Organism organism)
                throws IOException {

            // Set defaults
            this();

            // Check there's nothing
            if (!XmlHelper.validate(ele,
                    new String[0],
                    new String[0],
                    new String[] {
                            KEY_ELEM_AMP_STAGE
                    },
                    new String[0])) {
                throw new IOException("Found unrecognised element or attribute in AMP");
            }

            // Set args
            this.outputDir = outputDir;
            this.jobPrefix = jobPrefix;
            this.inputAssembly = inputAssembly;
            this.bubbleFile = bubbleFile;
            this.allLibraries = allLibraries;
            this.allMecqs = allMecqs;
            this.organism = organism;

            // Parse Xml for AMP stages
            // All single mass args
            NodeList nodes = ele.getElementsByTagName(KEY_ELEM_AMP_STAGE);
            for(int i = 1; i <= nodes.getLength(); i++) {

                String stageName = "amp-" + Integer.toString(i);
                File stageOutputDir = new File(this.getOutputDir(), stageName);

                AmpStage.Args stage = new AmpStage.Args(
                        (Element)nodes.item(i-1), stageOutputDir, this.getAssembliesDir(), jobPrefix + "-" + stageName,
                        this.allLibraries, this.allMecqs, this.organism,
                        this.inputAssembly, this.bubbleFile, i);

                this.stageArgsList.add(stage);

                this.inputAssembly = stage.getOutputFile();
            }
        }

        public File getInputAssembly() {
            return inputAssembly;
        }

        public void setInputAssembly(File inputAssembly) {
            this.inputAssembly = inputAssembly;
        }

        public File getBubbleFile() {
            return bubbleFile;
        }

        public void setBubbleFile(File bubbleFile) {
            this.bubbleFile = bubbleFile;
        }

        public File getOutputDir() {
            return outputDir;
        }

        public void setOutputDir(File outputDir) {
            this.outputDir = outputDir;
        }

        public List<Library> getAllLibraries() {
            return allLibraries;
        }

        public void setAllLibraries(List<Library> allLibraries) {
            this.allLibraries = allLibraries;
        }

        public String getJobPrefix() {
            return jobPrefix;
        }

        public void setJobPrefix(String jobPrefix) {
            this.jobPrefix = jobPrefix;
        }

        public List<Mecq.EcqArgs> getAllMecqs() {
            return allMecqs;
        }

        public void setAllMecqs(List<Mecq.EcqArgs> allMecqs) {
            this.allMecqs = allMecqs;
        }

        public List<AmpStage.Args> getStageArgsList() {
            return stageArgsList;
        }

        public void setStageArgsList(List<AmpStage.Args> stageArgsList) {
            this.stageArgsList = stageArgsList;
        }

        public Organism getOrganism() {
            return organism;
        }

        public void setOrganism(Organism organism) {
            this.organism = organism;
        }

        public File getAssembliesDir() {
            return new File(this.getOutputDir(), "assemblies");
        }

        public ExecutionContext getExecutionContext() {
            return executionContext;
        }

        public void setExecutionContext(ExecutionContext executionContext) {
            this.executionContext = executionContext;
        }

        @Override
        public void parseCommandLine(CommandLine cmdLine) {

        }

        @Override
        public ParamMap getArgMap() {

            Params params = (Params)this.params;
            ParamMap pvp = new DefaultParamMap();

            if (this.inputAssembly != null)
                pvp.put(params.getInputAssembly(), this.inputAssembly.getAbsolutePath());

            if (this.bubbleFile != null)
                pvp.put(params.getBubbleFile(), this.bubbleFile.getAbsolutePath());

            if (this.outputDir != null)
                pvp.put(params.getOutputDir(), this.outputDir.getAbsolutePath());

            if (this.jobPrefix != null)
                pvp.put(params.getJobPrefix(), this.jobPrefix);

            return pvp;
        }

        @Override
        protected void setOptionFromMapEntry(ConanParameter param, String value) {

            Params params = (Params)this.params;

            if (param.equals(params.getInputAssembly())) {
                this.inputAssembly = new File(value);
            } else if (param.equals(params.getBubbleFile())) {
                this.bubbleFile = new File(value);
            } else if (param.equals(params.getOutputDir())) {
                this.outputDir = new File(value);
            } else if (param.equals(params.getJobPrefix())) {
                this.jobPrefix = value;
            }
        }

        @Override
        protected void setArgFromMapEntry(ConanParameter param, String value) {

        }

        public File getFinalAssembly() {
            return new File(this.getOutputDir(), "final.fa");
        }

        @Override
        public List<ConanProcess> getExternalProcesses() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }
    }


    public static class Params extends AbstractProcessParams {

        private ConanParameter inputAssembly;
        private ConanParameter bubbleFile;
        private ConanParameter outputDir;
        private ConanParameter processes;
        private ConanParameter jobPrefix;

        public Params() {

            this.inputAssembly = new PathParameter(
                    "input",
                    "The input assembly containing the assembly to enhance",
                    true);

            this.bubbleFile = new PathParameter(
                    "bubble",
                    "The assembly bubble file",
                    true);

            this.outputDir = new PathParameter(
                    "output",
                    "The output directory which should contain the enhancement steps",
                    true);

            this.processes = new ParameterBuilder()
                    .longName("processes")
                    .description("The processes to execute to enhance the assembly")
                    .isOptional(false)
                    .argValidator(ArgValidator.OFF)
                    .create();

            this.jobPrefix = new ParameterBuilder()
                    .longName("jobPrefix")
                    .description("Describes the jobs that will be executed as part of this pipeline")
                    .argValidator(ArgValidator.DEFAULT)
                    .create();
        }



        public ConanParameter getInputAssembly() {
            return inputAssembly;
        }

        public ConanParameter getBubbleFile() {
            return bubbleFile;
        }

        public ConanParameter getOutputDir() {
            return outputDir;
        }

        public ConanParameter getProcesses() {
            return processes;
        }

        public ConanParameter getJobPrefix() {
            return jobPrefix;
        }

        @Override
        public ConanParameter[] getConanParametersAsArray() {
            return new ConanParameter[]{
                    this.inputAssembly,
                    this.bubbleFile,
                    this.outputDir,
                    this.processes,
                    this.jobPrefix
            };
        }

    }


}
