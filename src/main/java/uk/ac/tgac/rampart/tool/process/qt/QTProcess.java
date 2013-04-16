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
package uk.ac.tgac.rampart.tool.process.qt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.ac.ebi.fgpt.conan.core.process.AbstractConanProcess;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionContext;
import uk.ac.ebi.fgpt.conan.model.context.ExitStatus;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.process.qt.QualityTrimmer;
import uk.ac.tgac.rampart.data.RampartConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: maplesod
 * Date: 07/01/13
 * Time: 10:54
 * To change this template use File | Settings | File Templates.
 */
@Component
public class QTProcess extends AbstractConanProcess {

    private static Logger log = LoggerFactory.getLogger(QTProcess.class);


    public QTProcess() {
        this(new QTArgs());
    }

    public QTProcess(QTArgs args) {
        super("", args, new QTParams());
    }


    @Override
    public boolean execute(ExecutionContext executionContext) throws ProcessExecutionException, InterruptedException {

        try {

            log.info("Starting Quality Trimming Process");

            // Create shortcuts for convienience
            QTArgs args = (QTArgs) this.getProcessArgs();
            String qtType = args.getQualityTrimmer();
            List<Library> libs = args.getLibs();
            List<QualityTrimmer> qtList = args.createQualityTrimmers();

            // If the output directory doesn't exist then make it
            if (!args.getOutputDir().exists()) {
                log.debug("Creating output directory");
                args.getOutputDir().mkdirs();
            }

            // Execute quality trimmers for each library
            if (!args.isNoQT()) {
                log.debug("Executing " + qtList.size() + " quality trimming processes");

                if (qtList.size() > 1) {
                    int i = 1;
                    for (QualityTrimmer qt : qtList) {
                         this.executeQualityTrimmer(qt, args.getJobPrefix(), args.isRunParallel(), args.getOutputDir(), i++, executionContext);
                    }

                    // If we're using a scheduler and we have been asked to run the quality trimming processes for each library
                    // in parallel, then we should wait for all those to complete before continueing.
                    if (executionContext.usingScheduler() && args.isRunParallel()) {
                        log.debug("Running Quality trimming step in parallel, waiting for completion");
                        this.executeScheduledWait(args.getJobPrefix(), args.getOutputDir(), executionContext);
                    }
                }
                else {
                    this.executeQualityTrimmer(qtList.get(0), args.getJobPrefix(), false, args.getOutputDir(), 1, executionContext);
                }
            }

            // If requested create modified configuration files which can be used to drive other RAMPART processes after
            // QT has completed.
            if (args.isCreateConfigs()) {
                log.debug("Creating RAMPART configuration files for next process");
                this.createConfigs(args);
            }

            log.info("Quality trimming complete");

        } catch (IOException ioe) {
            throw new ProcessExecutionException(-1, ioe);
        }

        return true;
    }


    protected void executeQualityTrimmer(QualityTrimmer qualityTrimmer, String jobPrefix, boolean runInParallel,
                                         File outputDir, int index, ExecutionContext executionContext)
            throws ProcessExecutionException, InterruptedException {

        // Duplicate the execution context so we don't modify the original accidentally.
        ExecutionContext executionContextCopy = executionContext.copy();

        // Ensure downstream process has access to the process service
        qualityTrimmer.configure(this.getConanProcessService());

        if (executionContext.usingScheduler()) {

            String jobName = jobPrefix + "_" + index++;
            executionContextCopy.getScheduler().getArgs().setJobName(jobName);
            executionContextCopy.getScheduler().getArgs().setMonitorFile(new File(outputDir, jobName + ".log"));
            executionContextCopy.setForegroundJob(!runInParallel);
        }

        this.conanProcessService.execute(qualityTrimmer, executionContextCopy);
    }

    protected void executeScheduledWait(String jobPrefix, File outputDir, ExecutionContext executionContext)
            throws ProcessExecutionException, InterruptedException {

        // Duplicate the execution context so we don't modify the original accidentally.
        ExecutionContext executionContextCopy = executionContext.copy();

        if (executionContext.usingScheduler()) {

            String jobName = jobPrefix + "_wait";
            executionContextCopy.getScheduler().getArgs().setJobName(jobName);
            executionContextCopy.getScheduler().getArgs().setMonitorFile(new File(outputDir, jobName + ".log"));
            executionContextCopy.setForegroundJob(true);
        }

        this.conanProcessService.waitFor(
                executionContextCopy.getScheduler().createWaitCondition(ExitStatus.Type.COMPLETED_SUCCESS, jobPrefix + "*"),
                executionContextCopy);
    }

    private void createConfigs(QTArgs args) throws IOException {

        RampartConfiguration rawConfig = null;
        RampartConfiguration qtConfig = null;

        // Create the basics of the configuration object, preferably using the original config file if present
        if (args.getConfig() != null && args.getConfig().exists()) {
            rawConfig = RampartConfiguration.loadFile(args.getConfig());
            qtConfig = RampartConfiguration.loadFile(args.getConfig());
        } else {
            throw new IOException("Configuration file was not provided, so raw and qt specific configs cannot be created");
        }

        // Modify the dataset names
        rawConfig.getJob().setName("raw");
        qtConfig.getJob().setName("qt");

        for(Library lib : rawConfig.getLibs()) {
            lib.setDataset(Library.Dataset.RAW);
        }

        for(Library lib : qtConfig.getLibs()) {
            lib.setDataset(Library.Dataset.QT);
        }

        // Modify the QT libs
        qtConfig.setLibs(args.createQtLibs());

        // Save configs to disk
        File rawConfigFile = new File(args.getOutputDir(), "raw.cfg");
        File qtConfigFile = new File(args.getOutputDir(), "qt.cfg");

        rawConfig.save(rawConfigFile);

        // Only bother saving QT args if user requested it
        if (!args.isNoQT()) {
            qtConfig.save(qtConfigFile);
        }
    }


    @Override
    public String getName() {
        return "QT";
    }

    @Override
    public String getCommand() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

}