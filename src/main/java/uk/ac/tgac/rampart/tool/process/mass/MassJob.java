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

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import uk.ac.ebi.fgpt.conan.core.context.DefaultExecutionContext;
import uk.ac.ebi.fgpt.conan.core.param.ArgValidator;
import uk.ac.ebi.fgpt.conan.core.param.ParameterBuilder;
import uk.ac.ebi.fgpt.conan.core.param.PathParameter;
import uk.ac.ebi.fgpt.conan.core.process.AbstractConanProcess;
import uk.ac.ebi.fgpt.conan.core.process.AbstractProcessArgs;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionContext;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionResult;
import uk.ac.ebi.fgpt.conan.model.context.ExitStatus;
import uk.ac.ebi.fgpt.conan.model.context.SchedulerArgs;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.param.ParamMap;
import uk.ac.ebi.fgpt.conan.service.ConanExecutorService;
import uk.ac.ebi.fgpt.conan.service.ConanProcessService;
import uk.ac.ebi.fgpt.conan.service.exception.ConanParameterException;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.ebi.fgpt.conan.util.StringJoiner;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.Organism;
import uk.ac.tgac.conan.core.util.XmlHelper;
import uk.ac.tgac.conan.process.asm.*;
import uk.ac.tgac.conan.process.ec.AbstractErrorCorrector;
import uk.ac.tgac.conan.process.subsampler.SubsamplerV1_0Args;
import uk.ac.tgac.conan.process.subsampler.SubsamplerV1_0Process;
import uk.ac.tgac.rampart.RampartJobFileSystem;
import uk.ac.tgac.rampart.tool.process.Mecq;
import uk.ac.tgac.rampart.tool.process.ReadsInput;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MassJob extends AbstractConanProcess {

    private static Logger log = LoggerFactory.getLogger(MassJob.class);

    private List<Integer> jobIds;

    public MassJob() {
        this(null);
    }

    public MassJob(ConanExecutorService ces) {
        this(ces, new Args());
    }

    public MassJob(ConanExecutorService ces, Args args) {
        super("", args, new Params(), ces);
        this.jobIds = new ArrayList<>();
    }

    public Args getArgs() {
        return (Args)this.getProcessArgs();
    }

    /**
     * Dispatches assembly jobs to the specified environments
     *
     * @param executionContext The environment to dispatch jobs too
     * @throws IOException
     * @throws IllegalArgumentException
     * @throws ProcessExecutionException
     * @throws InterruptedException
     */
    @Override
    public boolean execute(ExecutionContext executionContext) throws ProcessExecutionException, InterruptedException {

        try {

            // Make a shortcut to the args
            Args args = this.getArgs();

            log.info("Starting Single MASS run for \"" + args.getName() + "\"");

            Assembler genericAssembler = args.getGenericAssembler();
            Assembler.Type type = genericAssembler.getType();

            // Create any required directories for this job
            this.createSupportDirectories(genericAssembler);
            log.debug("Created directories in: \"" + args.getOutputDir() + "\"");

            jobIds.clear();

            Map<Integer, List<Integer>> ssResults = new HashMap<>();

            // Iterate over coverage range, and do subsampling if required
            for (Integer cvg : args.getCoverageRange()) {

                // Do subsampling for this coverage level if required
                ssResults.put(cvg, this.doSubsampling(genericAssembler.doesSubsampling(), cvg, args.getSelectedLibs(),
                        args.isRunParallel()));
            }

            for(Assembler assembler : args.getAssemblers()) {

                AbstractAssemblerArgs asmArgs = assembler.getAssemblerArgs();
                File outputDir = asmArgs.getOutputDir();

                log.debug("Starting '" + args.getTool() + "' in \"" + outputDir.getAbsolutePath() + "\"");

                // Make the output directory for this child job (delete the directory if it already exists)
                if (outputDir.exists()) {
                    FileUtils.deleteDirectory(outputDir);
                }
                outputDir.mkdirs();

                // Execute the assembler
                ExecutionResult result = this.executeAssembler(assembler, args.getJobPrefix() +
                        "-assembly-" + outputDir.getName(), args.isRunParallel(), ssResults.get(asmArgs.getDesiredCoverage()));

                // Add assembler id to list
                jobIds.add(result.getJobId());

                // Create links for outputs from this assembler to known locations
                this.createAssemblyLinks(assembler, args, args.getName() + "-" + outputDir.getName());
            }

            // Check to see if we should run each MASS group in parallel, if not wait here until each MASS group has completed
            if (executionContext.usingScheduler() && !args.isMassParallel() && args.isRunParallel()) {
                log.debug("Waiting for completion of: " + args.getName());
                this.conanExecutorService.executeScheduledWait(
                        jobIds,
                        args.getJobPrefix() + "-mass-*",
                        ExitStatus.Type.COMPLETED_ANY,
                        args.getJobPrefix() + "-wait",
                        args.getOutputDir());
            }


            // Finish
            log.info("Finished MASS group: \"" + args.getName() + "\"");

        } catch (IOException | ConanParameterException ioe) {
            throw new ProcessExecutionException(-1, ioe);
        }

        return true;
    }


    public List<Integer> getJobIds() {
        return this.jobIds;
    }

    private class SubsamplingResult {
        private List<Library> libraries;
        private List<Integer> jobIds;

        private SubsamplingResult(List<Library> libraries, List<Integer> jobIds) {
            this.libraries = libraries;
            this.jobIds = jobIds;
        }

        public List<Library> getLibraries() {
            return libraries;
        }

        public List<Integer> getJobIds() {
            return jobIds;
        }
    }


    private List<Integer> doSubsampling(boolean assemblerDoesSubsampling, int coverage,
                                        List<Library> libraries, boolean runParallel)
            throws IOException, InterruptedException, ProcessExecutionException, ConanParameterException {

        Args args = this.getArgs();

        // Check to see if we even need to do subsampling.  If not just return the current libraries.
        if (assemblerDoesSubsampling || coverage == CoverageRange.ALL) {
            return new ArrayList<Integer>();
        }

        // Try to create directory to contain subsampled libraries
        File subsamplingDir = new File(args.getOutputDir(), "subsampled_libs");

        if (!subsamplingDir.exists()) {
            if (!subsamplingDir.mkdirs()) {
                throw new IOException("Couldn't create subsampling directory for " + args.getName() + " in " + args.getOutputDir().getAbsolutePath());
            }
        }

        // Subsample each library
        List<Integer> jobIds = new ArrayList<>();

        for(Library lib : libraries) {

            Library subsampledLib = lib.copy();

            // Subsample to this coverage level if required
            long timestamp = System.currentTimeMillis();
            String fileSuffix = "_cvg-" + coverage + ".fastq";
            String jobPrefix = args.getJobPrefix() + "-subsample-" + lib.getName() + "-" + coverage + "x";

            subsampledLib.setName(lib.getName() + "-" + coverage + "x");

            if (subsampledLib.isPairedEnd()) {

                // This calculation is much quicker if library is uniform, in this case we just calculate from the number
                // of entries, otherwise we have to scan the whole file.
                long sequencedBases = lib.isUniform() ?
                        2 * lib.getReadLength() * this.getNbEntries(lib.getFile1(), subsamplingDir, jobPrefix + "-file1-line_count") :
                        this.getNbBases(lib.getFile1(), subsamplingDir, jobPrefix + "-file1-base_count") +
                                this.getNbBases(lib.getFile2(), subsamplingDir, jobPrefix + "-file2-base_count");

                // Calculate the probability of keeping an entry
                double probability = (double)sequencedBases / (double)args.getOrganism().getEstGenomeSize() / 2.0;

                log.debug("Estimated that library: " + lib.getName() + "; has approximately " + sequencedBases + " bases.  " +
                        "Estimated genome size is: " + args.getOrganism().getEstGenomeSize() + "; so we plan only to keep " +
                        probability + "% of the reads to achieve approximately " + coverage + "X coverage");


                subsampledLib.setFiles(
                        new File(subsamplingDir, lib.getFile1().getName() + fileSuffix),
                        new File(subsamplingDir, lib.getFile2().getName() + fileSuffix)
                );

                ExecutionResult f1Result = this.executeSubsampler(probability, timestamp,
                        lib.getFile1(), subsampledLib.getFile1(), jobPrefix + "-file1", runParallel);

                ExecutionResult f2Result = this.executeSubsampler(probability, timestamp,
                        lib.getFile2(), subsampledLib.getFile2(), jobPrefix + "-file2", runParallel);

                jobIds.add(f1Result.getJobId());
                jobIds.add(f2Result.getJobId());
            }
            else {

                // This calculation is much quicker if library is uniform, in this case we just calculate from the number
                // of entries, otherwise we have to scan the whole file.
                long sequencedBases = lib.isUniform() ?
                        lib.getReadLength() * this.getNbEntries(lib.getFile1(), subsamplingDir, jobPrefix + "-file1-line_count") :
                        this.getNbBases(lib.getFile1(), subsamplingDir, jobPrefix + "-file1-base_count");

                // Calculate the probability of keeping an entry
                double probability = (double)sequencedBases / (double)args.getOrganism().getEstGenomeSize();

                log.debug("Estimated that library: " + lib.getName() + "; has approximately " + sequencedBases + " bases.  " +
                        "Estimated genome size is: " + args.getOrganism().getEstGenomeSize() + "; so we plan only to keep " +
                        probability + "% of the reads to achieve approximately " + coverage + "X coverage");

                subsampledLib.setFiles(
                        new File(subsamplingDir, lib.getFile1().getName() + fileSuffix),
                        null
                );

                ExecutionResult result = this.executeSubsampler(probability, timestamp,
                        lib.getFile1(), subsampledLib.getFile1(), jobPrefix, runParallel);

                jobIds.add(result.getJobId());
            }
        }

        return jobIds;
    }

    /**
     * Create output directories that contain symbolic links to to all the assemblies generated during this run
     * @param assembler
     */
    protected void createSupportDirectories(Assembler assembler) {

        Args args = this.getArgs();

        // Create directory for links to assembled contigs
        if (assembler.makesUnitigs()) {
            args.getUnitigsDir().mkdir();
        }

        // Create directory for links to assembled contigs
        if (assembler.makesContigs()) {
            args.getContigsDir().mkdir();
        }

        // Create dir for scaffold links if this asm creates them
        if (assembler.makesScaffolds()) {
            args.getScaffoldsDir().mkdir();
        }
    }


    protected File getHighestStatsLevelDir() {

        Args args = this.getArgs();

        if (args.getScaffoldsDir().exists()) {
            return args.getScaffoldsDir();
        }
        else if (args.getContigsDir().exists()) {
            return args.getContigsDir();
        }
        else if (args.getUnitigsDir().exists()) {
            return args.getUnitigsDir();
        }

        return null;
    }

    /**
     * Gets all the CEGMA files in the directory specified by the user.
     * @param cegmaDir
     * @return A list of CEGMA files in the current directory
     */
    protected List<File> getCegmaFiles(File cegmaDir) {

        if (cegmaDir == null || !cegmaDir.exists())
            return null;

        List<File> fileList = new ArrayList<>();

        Collection<File> fileCollection = FileUtils.listFiles(cegmaDir, new String[]{"cegma"}, true);

        for(File file : fileCollection) {
            fileList.add(file);
        }

        Collections.sort(fileList);

        return fileList;
    }


    @Override
    public String getName() {
        return "MASS";
    }

    @Override
    public boolean isOperational(ExecutionContext executionContext) {

        Args args = this.getArgs();

        final String toolErrMsg = "\"Unidentified assembler \"" + args.getTool() + "\" requested for MASS job: \"" + args.getName() + "\"";

        Assembler asm = AssemblerFactory.createGenericAssembler(args.getTool(), this.conanExecutorService);

        if (asm == null) {
            throw new NullPointerException(toolErrMsg);
        }

        if (!validConfig(asm)) {
            log.warn("Assembler \"" + args.getTool() + "\" does not have a compatible MASS job configuration: \"" + args.getName() + "\"");
            return false;
        }

        if (!asm.isOperational(executionContext)) {

            log.warn("Assembler \"" + args.getTool() + "\" used in MASS job \"" + args.getName() + "\" is NOT operational.");
            return false;
        }

        log.info("Single MASS process \"" + args.getName() + "\" is operational.");

        return true;
    }

    private boolean validConfig(Assembler asm) {

        Args args = this.getArgs();

        if (asm.getType() == Assembler.Type.DE_BRUIJN || asm.getType() == Assembler.Type.DE_BRUIJN_OPTIMISER) {
            if (args.getKmerRange() == null) {
                throw new IllegalArgumentException("MASS job \"" + args.getName() + "\" is a De Bruijn graph assembler (or optimiser) but you have not specified a Kmer range to process in this MASS job.");
            }
        }
        else if (asm.getType() == Assembler.Type.DE_BRUIJN_AUTO || asm.getType() == Assembler.Type.OVERLAP_LAYOUT_CONSENSUS) {
            if (args.getKmerRange() != null) {
                log.warn("MASS job \"" + args.getName() + "\" does not support Kmer ranges, but you have specified one.");
                return false;
            }
        }

        return true;
    }

    @Override
    public String getCommand() {
        return null;
    }

    public long getNbEntries(File seqFile, File outputDir, String jobName) throws ProcessExecutionException, InterruptedException, IOException {

        return getCount(seqFile, outputDir, jobName, true);
    }

    public long getNbBases(File seqFile, File outputDir, String jobName) throws IOException, ProcessExecutionException, InterruptedException {

        return getCount(seqFile, outputDir, jobName, false);
    }

    protected long getCount(File seqFile, File outputDir, String jobName, boolean linesOnly) throws ProcessExecutionException, InterruptedException, IOException {

        File outputFile = new File(outputDir, seqFile.getName() + ".nb_entries.out");

        String wcOption = linesOnly ? "-l" : "-m";
        String command = "awk '/^@/{getline; print}' " + seqFile.getAbsolutePath() + " | wc " + wcOption + " > " + outputFile.getAbsolutePath();

        this.conanExecutorService.executeProcess(command, outputDir, jobName, 1, 1, false);

        List<String> lines = FileUtils.readLines(outputFile);

        if (lines == null || lines.isEmpty()) {
            throw new IOException("Failed to retrieve number of lines in file: " + seqFile.getAbsolutePath());
        }

        return Long.parseLong(lines.get(0).trim());
    }

    public ExecutionResult executeAssembler(Assembler assembler, String jobName, boolean runParallel, List<Integer> jobIds)
            throws ProcessExecutionException, InterruptedException, IOException, ConanParameterException {

        // Important that this happens after directory cleaning.
        assembler.setup();

        AbstractAssemblerArgs asmArgs = assembler.getAssemblerArgs();

        ConanProcessService cps = this.conanExecutorService.getConanProcessService();

        // Create execution context
        ExecutionContext executionContextCopy = this.conanExecutorService.getExecutionContext().copy();
        executionContextCopy.setContext(
                jobName,
                executionContextCopy.usingScheduler() ? !runParallel : true,
                new File(assembler.getAssemblerArgs().getOutputDir(), jobName + ".log"));

        // Modify the scheduling settings if present
        if (this.conanExecutorService.usingScheduler()) {

            SchedulerArgs schArgs = executionContextCopy.getScheduler().getArgs();

            schArgs.setThreads(asmArgs.getThreads());
            schArgs.setMemoryMB(asmArgs.getMaxMemUsageMB());

            // Add wait condition for subsampling jobs (or any other jobs that must finish first), assuming there are any
            if (jobIds != null && !jobIds.isEmpty()) {
                schArgs.setWaitCondition(executionContextCopy.getScheduler().createWaitCondition(
                        ExitStatus.Type.COMPLETED_ANY, jobIds));
            }

            if (assembler.usesOpenMpi() && asmArgs.getThreads() > 1) {
                schArgs.setOpenmpi(true);
            }
        }

        // Create process
        return cps.execute(assembler, executionContextCopy);
    }

    public void createAssemblyLinks(Assembler assembler, Args smArgs, String jobName)
            throws ProcessExecutionException, InterruptedException {

        ExecutionContext linkingExecutionContext = new DefaultExecutionContext(
                this.conanExecutorService.getExecutionContext().getLocality(), null, null);

        ConanProcessService cps = this.conanExecutorService.getConanProcessService();

        StringJoiner compoundLinkCmdLine = new StringJoiner(";");

        if (assembler.makesUnitigs()) {
            compoundLinkCmdLine.add(
                    cps.makeLinkCommand(assembler.getUnitigsFile(),
                            new File(smArgs.getUnitigsDir(), jobName + "-unitigs.fa")));
        }

        if (assembler.makesContigs()) {
            compoundLinkCmdLine.add(
                    cps.makeLinkCommand(assembler.getContigsFile(),
                            new File(smArgs.getContigsDir(), jobName + "-contigs.fa")));
        }

        if (assembler.makesScaffolds()) {
            compoundLinkCmdLine.add(
                    cps.makeLinkCommand(assembler.getScaffoldsFile(),
                            new File(smArgs.getScaffoldsDir(), jobName + "-scaffolds.fa")));
        }

        cps.execute(compoundLinkCmdLine.toString(), linkingExecutionContext);
    }


    public ExecutionResult executeSubsampler(double probability, long timestamp, File input, File output, String jobName,
                                             boolean runParallel)
            throws ProcessExecutionException, InterruptedException, IOException, ConanParameterException {

        SubsamplerV1_0Args ssArgs = new SubsamplerV1_0Args();
        ssArgs.setInputFile(input);
        ssArgs.setOutputFile(output);
        ssArgs.setLogFile(new File(output.getParentFile(), output.getName() + ".log"));
        ssArgs.setSeed(timestamp);
        ssArgs.setProbability(probability);

        SubsamplerV1_0Process ssProc = new SubsamplerV1_0Process(ssArgs);

        // Create process
        return this.conanExecutorService.executeProcess(ssProc, output.getParentFile(), jobName, 1, 2000,
                this.conanExecutorService.usingScheduler() ? runParallel : false);
    }


    public static class Args extends AbstractProcessArgs {

        private static final String KEY_ELEM_INPUTS = "inputs";
        private static final String KEY_ELEM_SINGLE_INPUT = "input";
        private static final String KEY_ELEM_KMER_RANGE = "kmer";
        private static final String KEY_ELEM_CVG_RANGE = "coverage";

        private static final String KEY_ATTR_NAME = "name";
        private static final String KEY_ATTR_TOOL = "tool";
        private static final String KEY_ATTR_THREADS = "threads";
        private static final String KEY_ATTR_MEMORY = "memory";
        private static final String KEY_ATTR_PARALLEL = "parallel";

        public static final boolean DEFAULT_STATS_ONLY = false;
        public static final boolean DEFAULT_RUN_PARALLEL = false;
        public static final boolean DEFAULT_MASS_PARALLEL = false;
        public static final int DEFAULT_THREADS = 1;
        public static final int DEFAULT_MEMORY = 0;


        // Class vars
        private File outputDir;
        private String jobPrefix;

        private String name;
        private String tool;
        private KmerRange kmerRange;
        private CoverageRange coverageRange;
        private int coverageCutoff;
        private Organism organism;

        // Inputs
        private File mecqDir;
        private List<ReadsInput> inputs;
        private List<Library> allLibraries;
        private List<Mecq.EcqArgs> allMecqs;

        // System settings
        private int threads;
        private int memory;
        private boolean runParallel;
        private boolean massParallel;
        private List<Library> selectedLibs;

        // Temp vars
        private Assembler genericAssembler;
        private List<Assembler> assemblers;


        public Args() {

            super(new Params());

            this.outputDir = null;
            this.jobPrefix = "";

            this.tool = "ABYSS_V1.3";
            this.kmerRange = new KmerRange();
            this.coverageRange = new CoverageRange();
            this.coverageCutoff = 0;
            this.organism = null;

            this.mecqDir = null;
            this.inputs = new ArrayList<>();
            this.allLibraries = new ArrayList<>();
            this.allMecqs = new ArrayList<>();

            this.threads = 1;
            this.memory = 0;
            this.runParallel = DEFAULT_RUN_PARALLEL;
            this.massParallel = DEFAULT_MASS_PARALLEL;

            this.assemblers = new ArrayList<>();
        }



        public Args(Element ele, File parentOutputDir, File mecqDir, String parentJobPrefix, List<Library> allLibraries,
                              List<Mecq.EcqArgs> allMecqs, Organism organism, boolean massParallel) {

            // Set defaults
            this();

            // Required Attributes
            if (!ele.hasAttribute(KEY_ATTR_NAME))
                throw new IllegalArgumentException("Could not find " + KEY_ATTR_NAME + " attribute in MASS job element");

            if (!ele.hasAttribute(KEY_ATTR_TOOL))
                throw new IllegalArgumentException("Could not find " + KEY_ATTR_TOOL + " attribute in MASS job element");

            this.name = XmlHelper.getTextValue(ele, KEY_ATTR_NAME);
            this.tool = XmlHelper.getTextValue(ele, KEY_ATTR_TOOL);

            this.assemblers = new ArrayList<>();

            // Required Elements
            Element inputElements = XmlHelper.getDistinctElementByName(ele, KEY_ELEM_INPUTS);
            NodeList actualInputs = inputElements.getElementsByTagName(KEY_ELEM_SINGLE_INPUT);
            for(int i = 0; i < actualInputs.getLength(); i++) {
                this.inputs.add(new ReadsInput((Element) actualInputs.item(i)));
            }

            // Optional
            this.threads = ele.hasAttribute(KEY_ATTR_THREADS) ?
                    XmlHelper.getIntValue(ele, KEY_ATTR_THREADS) :
                    DEFAULT_THREADS;

            this.memory = ele.hasAttribute(KEY_ATTR_MEMORY) ?
                    XmlHelper.getIntValue(ele, KEY_ATTR_MEMORY) :
                    DEFAULT_MEMORY;

            this.runParallel = ele.hasAttribute(KEY_ATTR_PARALLEL) ?
                    XmlHelper.getBooleanValue(ele, KEY_ATTR_PARALLEL) :
                    DEFAULT_RUN_PARALLEL;


            // Other args
            this.allLibraries = allLibraries;
            this.allMecqs = allMecqs;
            this.outputDir = new File(parentOutputDir, name);
            this.jobPrefix = parentJobPrefix + "-" + name;
            this.organism = organism;
            this.mecqDir = mecqDir;
            this.massParallel = massParallel;

            // Setup input and generic assembler
            this.genericAssembler = this.createGenericAssembler();
            this.selectedLibs = this.selectInputs();
            this.genericAssembler.setLibraries(this.selectedLibs);

            Element kmerElement = XmlHelper.getDistinctElementByName(ele, KEY_ELEM_KMER_RANGE);
            this.kmerRange = kmerElement != null ?
                    new KmerRange(kmerElement, 35, KmerRange.getLastKmerFromLibs(selectedLibs), KmerRange.StepSize.MEDIUM.getStep()) :
                    null;

            Element cvgElement = XmlHelper.getDistinctElementByName(ele, KEY_ELEM_CVG_RANGE);
            this.coverageRange = cvgElement != null ?
                    new CoverageRange(cvgElement) :
                    new CoverageRange();

            this.initialise(false);
        }

        public final void initialise() {
            this.initialise(true);
        }

        protected final void initialise(boolean createGenericAssembler) {

            if (createGenericAssembler) {
                // Setup input and generic assembler
                this.genericAssembler = this.createGenericAssembler();
                this.selectedLibs = this.selectInputs();
                this.genericAssembler.setLibraries(this.selectedLibs);
            }

            this.validateKmerRange();
            this.validateCoverageRange();

            this.assemblers = this.createAssemblers();
        }

        public final Assembler createGenericAssembler() {

            Assembler genericAssembler = AssemblerFactory.createGenericAssembler(this.tool);

            if (genericAssembler == null)
                throw new IllegalArgumentException("Could not find assembler: " + this.tool);

            return genericAssembler;
        }

        public final List<Library> selectInputs() {

            List<Library> selectedLibs = new ArrayList<>();

            for(ReadsInput mi : inputs) {
                Library lib = mi.findLibrary(allLibraries);
                Mecq.EcqArgs ecqArgs = mi.findMecq(allMecqs);

                if (lib == null) {
                    throw new IllegalArgumentException("Unrecognised library: " + mi.getLib() + "; not processing MASS run: " + name);
                }

                if (ecqArgs == null) {
                    if (mi.getEcq().equalsIgnoreCase(Mecq.EcqArgs.RAW)) {
                        selectedLibs.add(lib);
                    }
                    else {
                        throw new IllegalArgumentException("Unrecognised MECQ dataset requested: " + mi.getEcq() + "; not processing MASS run: " + name);
                    }
                }
                else {
                    Library modLib = lib.copy();

                    AbstractErrorCorrector ec = ecqArgs.makeErrorCorrector(modLib);
                    List<File> files = ec.getArgs().getCorrectedFiles();

                    if (modLib.isPairedEnd()) {
                        if (files.size() < 2 || files.size() > 3) {
                            throw new IllegalArgumentException("Paired end library: " + modLib.getName() + " from " + ecqArgs.getName() + " does not have two or three files");
                        }

                        modLib.setFiles(files.get(0), files.get(1));
                    }
                    else {
                        if (files.size() != 1) {
                            throw new IllegalArgumentException("Single end library: " + modLib.getName() + " from " + ecqArgs.getName() + " does not have one file");
                        }

                        modLib.setFiles(files.get(0), null);
                    }

                    selectedLibs.add(modLib);
                }

                log.info("Found library.  Lib name: " + mi.getLib() + "; ECQ name: " + mi.getEcq() + "; Job name: " + name);
            }

            return selectedLibs;
        }

        protected final void validateKmerRange() {

            if (genericAssembler.getType() != Assembler.Type.DE_BRUIJN && genericAssembler.getType() != Assembler.Type.DE_BRUIJN_OPTIMISER) {
                this.kmerRange = null;  // Force to null
                log.warn("The selected assembler \"" + this.tool + "\" for job \"" + this.name + "\" does not support K parameter");
            }
            else if (kmerRange == null) {
                this.kmerRange = new KmerRange(35, KmerRange.getLastKmerFromLibs(selectedLibs), KmerRange.StepSize.MEDIUM);
                log.info("No K-mer range specified for \"" + this.name + "\" running assembler with default range: " + this.kmerRange.toString());
            }
            else if (kmerRange.validate()) {
                log.info("K-mer range for \"" + this.name + "\" validated: " + kmerRange.toString());
            }
            else {
                throw new IllegalArgumentException("Invalid K-mer range: " + kmerRange.toString() + " Not processing MASS job: " + this.name);
            }
        }

        protected final void validateCoverageRange() {

            if (coverageRange == null) {
                CoverageRange defaultCoverageRange = new CoverageRange();
                log.info("No coverage range specified for \"" + this.name + "\" running assembler with default range: " + defaultCoverageRange.toString());
            }
            else if (organism == null || organism.getEstGenomeSize() <= 0) {
                CoverageRange defaultCoverageRange = new CoverageRange();
                log.info("No estimated genome size specified.  Not possible to subsample to desired range without a genome " +
                        "size estimate. Running assembler with default coverage range: " + defaultCoverageRange.toString());
            }
            else if (coverageRange.validate()) {
                log.info("Coverage range for \"" + this.name + "\" validated: " + coverageRange.toString());
            }
            else {
                throw new IllegalArgumentException("Invalid coverage range: " + coverageRange.toString() + " Not processing MASS run: \"" + this.name + "\"");
            }
        }

        private List<Library> createSubsampledLibPaths(int coverage) {

            // Check to see if we even need to do subsampling.  If not just return the current libraries.
            if (this.genericAssembler.doesSubsampling() || coverage == CoverageRange.ALL) {
                return this.selectedLibs;
            }

            // Try to create directory to contain subsampled libraries
            File subsamplingDir = new File(this.outputDir, "subsampled_libs");

            // Subsample each library
            List<Library> subsampledLibs = new ArrayList<>();
            List<Integer> jobIds = new ArrayList<>();

            for(Library lib : this.selectedLibs) {

                Library subsampledLib = lib.copy();

                // Subsample to this coverage level if required
                String fileSuffix = "_cvg-" + coverage + ".fastq";
                String jobPrefix = this.jobPrefix + "-subsample-" + lib.getName() + "-" + coverage + "x";

                subsampledLib.setName(lib.getName() + "-" + coverage + "x");

                if (subsampledLib.isPairedEnd()) {

                    subsampledLib.setFiles(
                            new File(subsamplingDir, lib.getFile1().getName() + fileSuffix),
                            new File(subsamplingDir, lib.getFile2().getName() + fileSuffix)
                    );
                }
                else {
                    subsampledLib.setFiles(
                            new File(subsamplingDir, lib.getFile1().getName() + fileSuffix),
                            null
                    );
                }

                subsampledLibs.add(subsampledLib);
            }

            return subsampledLibs;
        }

        protected final List<Assembler> createAssemblers() {

            List<Assembler> assemblers = new ArrayList<>();

            // Iterate over coverage range
            for (Integer cvg : this.coverageRange) {

                // Do subsampling for this coverage level if required
                List<Library> subsampledLibs = this.createSubsampledLibPaths(cvg);

                // Generate a directory name for this assembly
                String cvgString = CoverageRange.toString(cvg);
                String cvgDirName = "cvg-" + cvgString;

                // This is the output directory for this particular assembly
                File outputDir = new File(this.outputDir, cvgDirName);

                Assembler.Type type = this.genericAssembler.getType();

                if (type == Assembler.Type.DE_BRUIJN) {

                    // Iterate over kmer range
                    for (Integer k : this.kmerRange) {

                        // Override output dir name with k values
                        File kOutputDir = new File(this.outputDir, cvgDirName + "_k-" + k);

                        GenericDeBruijnArgs dbgArgs = new GenericDeBruijnArgs();
                        dbgArgs.setOutputDir(kOutputDir);
                        dbgArgs.setLibraries(subsampledLibs);
                        dbgArgs.setThreads(this.threads);
                        dbgArgs.setMemory(this.memory);
                        dbgArgs.setOrganism(this.organism);
                        dbgArgs.setDesiredCoverage(cvg);
                        dbgArgs.setK(k);

                        //TODO set other args here

                        // Create the actual assembler for these settings
                        assemblers.add(dbgArgs.createAssembler(this.tool));
                    }
                } else if (type == Assembler.Type.DE_BRUIJN_AUTO) {

                    GenericDeBruijnAutoArgs dbgAutoArgs = new GenericDeBruijnAutoArgs();
                    dbgAutoArgs.setOutputDir(outputDir);
                    dbgAutoArgs.setLibraries(subsampledLibs);
                    dbgAutoArgs.setThreads(this.threads);
                    dbgAutoArgs.setMemory(this.memory);
                    dbgAutoArgs.setOrganism(this.organism);
                    dbgAutoArgs.setDesiredCoverage(cvg);

                    // Create the actual assembler for these settings
                    assemblers.add(dbgAutoArgs.createAssembler(this.tool));

                } else if (type == Assembler.Type.DE_BRUIJN_OPTIMISER) {

                    GenericDeBruijnOptimiserArgs dbgOptArgs = new GenericDeBruijnOptimiserArgs();
                    dbgOptArgs.setOutputDir(outputDir);
                    dbgOptArgs.setLibraries(subsampledLibs);
                    dbgOptArgs.setThreads(this.threads);
                    dbgOptArgs.setMemory(this.memory);
                    dbgOptArgs.setOrganism(this.organism);
                    dbgOptArgs.setKmerRange(this.kmerRange);

                    // Create the actual assembler for these settings
                    assemblers.add(dbgOptArgs.createAssembler(this.tool));

                } else {
                    throw new IllegalArgumentException("Unknown assembly type detected: " + type);
                }
            }

            return assemblers;
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

        public KmerRange getKmerRange() {
            return kmerRange;
        }

        public void setKmerRange(KmerRange kmerRange) {
            this.kmerRange = kmerRange;
        }

        public CoverageRange getCoverageRange() {
            return coverageRange;
        }

        public void setCoverageRange(CoverageRange coverageRange) {
            this.coverageRange = coverageRange;
        }

        public int getCoverageCutoff() {
            return coverageCutoff;
        }

        public void setCoverageCutoff(int coverageCutoff) {
            this.coverageCutoff = coverageCutoff;
        }

        public boolean isRunParallel() {
            return runParallel;
        }

        public void setRunParallel(boolean runParallel) {
            this.runParallel = runParallel;
        }

        public boolean isMassParallel() {
            return massParallel;
        }

        public void setMassParallel(boolean massParallel) {
            this.massParallel = massParallel;
        }

        public List<ReadsInput> getInputs() {
            return inputs;
        }

        public void setInputs(List<ReadsInput> inputs) {
            this.inputs = inputs;
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

        public List<Mecq.EcqArgs> getAllMecqs() {
            return allMecqs;
        }

        public void setAllMecqs(List<Mecq.EcqArgs> allMecqs) {
            this.allMecqs = allMecqs;
        }

        public File getMecqDir() {
            return mecqDir;
        }

        public void setMecqDir(File mecqDir) {
            this.mecqDir = mecqDir;
        }

        public File getUnitigsDir() {
            return new File(this.getOutputDir(), "unitigs");
        }

        public File getContigsDir() {
            return new File(this.getOutputDir(), "contigs");
        }

        public File getScaffoldsDir() {
            return new File(this.getOutputDir(), "scaffolds");
        }

        public Assembler getGenericAssembler() {
            return this.genericAssembler;
        }

        public List<Assembler> getAssemblers() {
            return assemblers;
        }

        public void setAssemblers(List<Assembler> assemblers) {
            this.assemblers = assemblers;
        }

        public List<File> getInputKmers() {

            RampartJobFileSystem fs = new RampartJobFileSystem(this.getMecqDir().getParentFile());

            List<File> inputKmers = new ArrayList<>();

            for(ReadsInput ri : this.inputs) {
                inputKmers.add(new File(fs.getAnalyseReadsDir(), "jellyfish_" + ri.getEcq() + "_" + ri.getLib() + "_0"));
            }

            return inputKmers;
        }

        public File getStatsFile(Mass.OutputLevel outputLevel) {

            File outputLevelStatsDir = null;

            if (outputLevel == Mass.OutputLevel.CONTIGS) {
                outputLevelStatsDir = this.getContigsDir();
            }
            else if (outputLevel == Mass.OutputLevel.SCAFFOLDS) {
                outputLevelStatsDir = this.getScaffoldsDir();
            }
            else {
                throw new IllegalArgumentException("Output Level not specified");
            }

            return new File(outputLevelStatsDir, "stats.txt");
        }

        @Override
        protected void setOptionFromMapEntry(ConanParameter param, String value) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        protected void setArgFromMapEntry(ConanParameter param, String value) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void parse(String args) {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public ParamMap getArgMap() {

            return null;
        }

        public List<Library> getSelectedLibs() {
            return selectedLibs;
        }
    }


    public static class Params extends Mass.Params {

        private ConanParameter config;
        private ConanParameter jobName;
        private ConanParameter outputDir;
        private ConanParameter jobPrefix;

        public Params() {

            this.config = new PathParameter(
                    "config",
                    "The rampart configuration file containing the libraries to assemble",
                    true);

            this.jobName = new ParameterBuilder()
                    .longName("job_name")
                    .description("The job name that distinguishes this MASS run from other mass runs that might be running in parallel.")
                    .argValidator(ArgValidator.DEFAULT)
                    .create();

            this.outputDir = new PathParameter(
                    "output",
                    "The output directory",
                    true);

            this.jobPrefix = new ParameterBuilder()
                    .longName("job_prefix")
                    .description("The job_prefix to be assigned to all sub processes in MASS.  Useful if executing with a scheduler.")
                    .argValidator(ArgValidator.DEFAULT)
                    .create();
        }

        public ConanParameter getConfig() {
            return config;
        }

        public ConanParameter getJobName() {
            return jobName;
        }

        public ConanParameter getOutputDir() {
            return outputDir;
        }

        public ConanParameter getJobPrefix() {
            return jobPrefix;
        }

        @Override
        public ConanParameter[] getConanParametersAsArray() {
            return (ConanParameter[])ArrayUtils.addAll(super.getConanParametersAsArray(),
                    new ConanParameter[]{
                            this.config,
                            this.jobName,
                            this.outputDir,
                            this.jobPrefix
            });
        }

    }

}
