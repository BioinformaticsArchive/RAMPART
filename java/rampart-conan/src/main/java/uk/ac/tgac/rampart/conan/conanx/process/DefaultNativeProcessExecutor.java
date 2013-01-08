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
package uk.ac.tgac.rampart.conan.conanx.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.ebi.fgpt.conan.utils.CommandExecutionException;
import uk.ac.ebi.fgpt.conan.utils.ProcessRunner;

import java.io.IOException;

/**
 * User: maplesod
 * Date: 08/01/13
 * Time: 10:39
 */
public class DefaultNativeProcessExecutor implements NativeProcessExecutor {

    private static Logger log = LoggerFactory.getLogger(DefaultNativeProcessExecutor.class);

    @Override
    public String[] execute(String command) throws CommandExecutionException, IOException {
        log.debug("Issuing command: [" + command + "]");
        ProcessRunner runner = new ProcessRunner();
        runner.redirectStderr(true);
        String[] output = runner.runCommmand(command);
        if (output.length > 0) {
            log.debug("Response from command [" + command + "]: " +
                    output.length + " lines, first line was " + output[0]);
        }
        return output;
    }

    @Override
    public int dispatch(String command) throws CommandExecutionException, IOException {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ProcessExecutionException interpretExitValue(int exitValue) {
        if (exitValue == 0) {
            return null;
        }
        else {
            return new ProcessExecutionException(exitValue);
        }
    }

}
