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
package uk.ac.tgac.rampart.conan.env.arch.ge;

import uk.ac.tgac.rampart.core.utils.StringJoiner;


public class LSFArgs extends GridEngineArgs {

	private String projectName = "";
	private LSFWaitCondition waitCondition = null;
	private boolean openmpi = false;
	private String extraLsfOptions = "";

	public String getProjectName() {
		return projectName;
	}
	
	public void setProjectName(String projectName) {
		this.projectName = projectName;
	}
	
	public LSFWaitCondition getWaitCondition() {
		return waitCondition;
	}

	public void setWaitCondition(LSFWaitCondition waitCondition) {
		this.waitCondition = waitCondition;
	}
	
	public String getExtraLsfOptions() {
		return extraLsfOptions;
	}

	public void setExtraLsfOptions(String extraLsfOptions) {
		this.extraLsfOptions = extraLsfOptions;
	}

	public boolean isOpenmpi() {
		return openmpi;
	}

	public void setOpenmpi(boolean openmpi) {
		this.openmpi = openmpi;
	}
	
	protected boolean validString(String str) {
		return str != null && !str.isEmpty();
	}
	
	
	protected String createSimpleOptions() {
		
		final int threads = this.getThreads();
		
		StringJoiner joiner = new StringJoiner(" ");
		
		joiner.add("-J ", this.getJobName());
		joiner.add("-q ", this.getQueueName());
		joiner.add(threads > 1, "-n ", String.valueOf(threads));
		joiner.add("-P ", this.projectName);
		joiner.add(this.openmpi, "-a ", "openmpi");
		joiner.add(this.waitCondition);
		joiner.add(this.extraLsfOptions);
		
		return joiner.toString();
	}

	protected String createUsageString() {
		
		final int threads = this.getThreads();
		final int mem = this.getMemoryMB();
		
		String span = threads > 1 ? "span[ptile=" + threads + "]" : "";
		String rusage = mem > 0 ? "rusage[mem=" + mem + "]" : "";
			
		return !span.isEmpty() || !rusage.isEmpty() ? "-R" + rusage + span : "";
	}
	
	@Override
	public String toString() {
		
		String simpleOptions = createSimpleOptions();
		String usage = createUsageString();
		
		StringJoiner joiner = new StringJoiner(" ");
		
		joiner.add(simpleOptions);
		joiner.add(usage);
			
		return joiner.toString();
	}

    @Override
    public GridEngineArgs copy() {

        LSFArgs copy = new LSFArgs();

        return copy;
    }
}
