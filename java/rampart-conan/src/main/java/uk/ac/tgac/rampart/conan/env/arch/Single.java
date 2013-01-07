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
package uk.ac.tgac.rampart.conan.env.arch;

import java.net.ConnectException;

import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.tgac.rampart.conan.env.EnvironmentArgs;
import uk.ac.tgac.rampart.conan.process.ExtendedProcess;


public class Single implements Architecture {

	@Override
	public boolean isGridEngine() {
		return false;
	}

	@Override
	public boolean submitProcess(ExtendedProcess process,	EnvironmentArgs args)
			throws IllegalArgumentException, ProcessExecutionException,
			InterruptedException, ConnectException {
		
		return false;
	}

}
