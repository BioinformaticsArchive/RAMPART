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
package uk.ac.tgac.rampart.conan.tool.process.clip.internal;

import uk.ac.ebi.fgpt.conan.model.ConanParameter;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.tgac.rampart.conan.tool.module.amp.AmpTask;
import uk.ac.tgac.rampart.conan.tool.process.clip.Clipper;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: maplesod
 * Date: 07/01/13
 * Time: 10:56
 * To change this template use File | Settings | File Templates.
 */
public class RampartClipperProcess implements Clipper {

    private RampartClipperArgs args;

    public RampartClipperProcess() {
        this(new RampartClipperArgs());
    }

    public RampartClipperProcess(RampartClipperArgs args) {
        this.args = args;
    }

    @Override
    public boolean execute(Map<ConanParameter, String> parameters) throws ProcessExecutionException, IllegalArgumentException, InterruptedException {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public String getName() {
        return "RAMPART Clipper";
    }

    @Override
    public Collection<ConanParameter> getParameters() {
        return new RampartClipperParams().getConanParameters();
    }

    @Override
    public void setInputAssembly(File inputFile) {
        this.args.setIn(inputFile);
    }

    @Override
    public File getOutputAssembly() {
        return this.args.getOut();
    }

    private void clip() throws IOException {

        if (this.args.getIn() == null || !this.args.getIn().exists()) {
            throw new IOException("Input file does not exist");
        }

        BufferedReader reader = new BufferedReader(new FileReader(this.args.getIn()));
        PrintWriter writer = new PrintWriter(new FileWriter(this.args.getOut()));

        // Ignore everything but the sequences
        // While loop handles multi-line sequences
        boolean firstLine = true;
        int nbSeqBases = 0;
        StringBuilder lastSeq = new StringBuilder();
        String lastHeader = "";
        String line = null;

        while ((line = reader.readLine()) != null) {

            if (!line.isEmpty()) {

                char firstChar = line.charAt(0);

                // If we have found a header line then increment stats for this seq (unless this is the first time here)
                if (firstChar == '>') {

                    if (firstLine) {

                        // Store header
                        lastHeader = line;

                        firstLine = false;
                        continue;
                    }
                    else {

                        // Print out the last sequence if it was big enough
                        if (nbSeqBases > this.args.getMinLen()) {
                            writer.println(lastHeader);
                            writer.println(lastSeq.toString());
                        }

                        // Store header and clean seq buffer
                        lastHeader = line;
                        lastSeq = new StringBuilder();
                        nbSeqBases = 0;
                    }
                }
                else {
                    lastSeq.append(line);
                    nbSeqBases += line.length();
                }
            }
        }

        writer.close();
        reader.close();
    }

}
