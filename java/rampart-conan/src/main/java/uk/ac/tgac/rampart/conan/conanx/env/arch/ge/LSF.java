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
package uk.ac.tgac.rampart.conan.conanx.env.arch.ge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.fgpt.conan.lsf.LSFProcessAdapter;
import uk.ac.ebi.fgpt.conan.lsf.LSFProcessEvent;
import uk.ac.ebi.fgpt.conan.lsf.LSFProcessListener;
import uk.ac.ebi.fgpt.conan.properties.ConanProperties;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.ebi.fgpt.conan.utils.CommandExecutionException;
import uk.ac.ebi.fgpt.conan.utils.ProcessRunner;
import uk.ac.ebi.fgpt.conan.utils.ProcessUtils;
import uk.ac.tgac.rampart.conan.conanx.env.EnvironmentArgs;
import uk.ac.tgac.rampart.conan.conanx.process.ExtendedConanProcess;
import uk.ac.tgac.rampart.core.utils.StringJoiner;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class LSF extends AbstractGridEngine {

    private static Logger log = LoggerFactory.getLogger(LSF.class);
    
    public static final String BSUB = "bsub";

    private int monitorInterval = 15;
    
    public LSF() {
        super(BSUB);
    }

    /**
     * Gets the monitoring interval, in seconds, to use when checking for output from LSF processes.
     *
     * @return the interval between monitoring polls for LSF output, in seconds
     */
    protected int getMonitorInterval() {
        return monitorInterval;
    }

    /**
     * Sets the monitoring interval, in seconds, to use when checking for output from LSF processes.
     *
     * @param monitorInterval the interval between monitoring polls for LSF output, in seconds
     */
    protected void setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
    }
    
    @Override
    public void submitCommand(String command, EnvironmentArgs envArgs)
            throws IllegalArgumentException, ProcessExecutionException, InterruptedException, ConnectException {

        //log.debug("Executing an LSF process with parameters: " + parameters);

        // get email address to use as backup in case process fails
        String backupEmail = ConanProperties.getProperty("lsf.backup.email");

        // Create command to execute
        StringJoiner sj = new StringJoiner(" ");
        sj.add(this.getSubmitCommand());
        sj.add(envArgs.toString());
        sj.add("-u " + backupEmail);
        sj.add("\"" + command + " 2>&1\"");

        String cmd = sj.toString();

        // set up monitoring of the lsfOutputFile
        String lsfOutputFile = envArgs.getCmdLineOutputFile().getAbsolutePath();
        InvocationTrackingLSFProcessListener listener = new InvocationTrackingLSFProcessListener();

        final LSFProcessAdapter adapter = new LSFProcessAdapter(lsfOutputFile, this.getMonitorInterval());
        adapter.addLSFProcessListener(listener);

        // Execute the command in the foreground as normal, however LSF jobs will hang around executing on a cluster node
        // for a while, so we need to wait for the job to complete before exiting this method as well.  We do this by monitoring
        // the LSF output file.
        super.submitCommand(cmd, envArgs);

        // process exit value, initialise to -1
        int exitValue = -1;

        // process monitoring
        try {
            log.debug("Monitoring process, waiting for completion");
            exitValue = listener.waitFor();
            log.debug("LSF Process completed with exit value " + exitValue);

            ProcessExecutionException pex = this.processExecutor.interpretExitValue(exitValue);
            if (pex == null) {
                return;
            }
            else {
                pex.setProcessOutput(adapter.getProcessOutput());
                pex.setProcessExecutionHost(adapter.getProcessExecutionHost());
                throw pex;
            }
        }
        finally {
            // this process DID start, so only delete output files to cleanup if the process actually exited,
            // and wasn't e.g. interrupted prior to completion
            if (exitValue != -1) {
                log.debug("Deleting " + lsfOutputFile);
                ProcessUtils.deleteFiles(envArgs.getCmdLineOutputFile());
            }
        }
    }


    /**
     * An {@link uk.ac.ebi.fgpt.conan.lsf.LSFProcessListener} that encapsulates the state of each invocation of a process and updates flags for
     * completion and success.  Processes using this listener implementation can block on {@link #waitFor()}, which only
     * returns once the LSF process being listened to is complete.
     */
    private class InvocationTrackingLSFProcessListener implements LSFProcessListener {
        private boolean complete;
        private int exitValue;

        private InvocationTrackingLSFProcessListener() {
            complete = false;
            exitValue = -1;
        }

        public void processComplete(LSFProcessEvent evt) {
            log.debug("File finished writing, process exit value was " + evt.getExitValue());
            exitValue = evt.getExitValue();
            complete = true;
            synchronized (this) {
                notifyAll();
            }
        }

        public void processUpdate(LSFProcessEvent evt) {
            // do nothing, not yet finished
            log.debug("File was modified");
            synchronized (this) {
                notifyAll();
            }
        }

        public void processError(LSFProcessEvent evt) {
            // something went wrong
            log.debug("File was deleted by an external process");
            exitValue = 1;
            complete = true;
            synchronized (this) {
                notifyAll();
            }
        }

        /**
         * Returns the success of the LSFProcess being listened to, only once complete.  This method blocks until
         * completion or an interruption occurs.
         *
         * @return the exit value of the underlying process
         * @throws InterruptedException if the thread was interrupted whilst waiting
         */
        public int waitFor() throws InterruptedException {
            while (!complete) {
                synchronized (this) {
                    wait();
                }
            }
            log.debug("Process completed: exitValue = " + exitValue);
            return exitValue;
        }
    }
}
