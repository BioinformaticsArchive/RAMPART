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
package uk.ac.tgac.rampart.pipeline.tool.pipeline.rampart;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.ac.ebi.fgpt.conan.model.ConanPipeline;
import uk.ac.ebi.fgpt.conan.model.ConanProcess;
import uk.ac.ebi.fgpt.conan.model.ConanUser;
import uk.ac.ebi.fgpt.conan.model.param.ConanParameter;
import uk.ac.tgac.rampart.pipeline.tool.pipeline.RampartStage;
import uk.ac.tgac.rampart.pipeline.tool.pipeline.amp.AmpParams;
import uk.ac.tgac.rampart.pipeline.tool.pipeline.amp.AmpPipeline;
import uk.ac.tgac.rampart.pipeline.tool.pipeline.amp.AmpProcess;
import uk.ac.tgac.rampart.pipeline.tool.process.analyser.length.LengthAnalysisProcess;
import uk.ac.tgac.rampart.pipeline.tool.process.mass.multi.MultiMassParams;
import uk.ac.tgac.rampart.pipeline.tool.process.mass.multi.MultiMassProcess;
import uk.ac.tgac.rampart.pipeline.tool.process.qt.QTParams;
import uk.ac.tgac.rampart.pipeline.tool.process.qt.QTProcess;
import uk.ac.tgac.rampart.pipeline.tool.process.report.ReportProcess;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: maplesod
 * Date: 07/01/13
 * Time: 10:48
 * To change this template use File | Settings | File Templates.
 */
@Component
public class RampartPipeline implements ConanPipeline {

    @Autowired
    private QTProcess qtProcess;

    @Autowired
    private MultiMassProcess multiMassProcess;

    @Autowired
    private AmpProcess ampProcess;

    @Autowired
    private LengthAnalysisProcess lengthAnalysisProcess;

    @Autowired
    private ReportProcess reportProcess;

    private List<RampartStage> stages;



    @Override
    public String getName() {
        return "RAMPART";
    }

    @Override
    public ConanUser getCreator() {
        return null;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isDaemonized() {
        return false;
    }


    // ***** Getters *****

    public QTProcess getQtProcess() {
        return qtProcess;
    }

    public AmpProcess getAmpProcess() {
        return ampProcess;
    }

    public MultiMassProcess getMultiMassProcess() {
        return multiMassProcess;
    }

    public LengthAnalysisProcess getLengthAnalysisProcess() {
        return lengthAnalysisProcess;
    }

    public ReportProcess getReportProcess() {
        return reportProcess;
    }

    public void setStages(String stages) {

        if (stages.trim().equalsIgnoreCase("ALL")) {
            stages = "QT,MASS,AMP,ANALYSE,REPORT";
        }

        String[] stageArray = stages.split(",");

        List<RampartStage> stageList = new ArrayList<RampartStage>();

        if (stageArray != null && stageArray.length != 0) {
            for(String stage : stageArray) {
                stageList.add(RampartStage.valueOf(stage.trim().toUpperCase()));
            }
        }

        this.stages = stageList;
    }

    public void setStages(List<RampartStage> stages) {
        this.stages = stages;
    }



    @Override
    public List<ConanProcess> getProcesses() {

        List<ConanProcess> list = new ArrayList<ConanProcess>();

        if (this.stages == null || this.stages.isEmpty() || this.stages.contains(RampartStage.QT)) {
            list.add(this.qtProcess);
        }

        if (this.stages == null || this.stages.isEmpty() || this.stages.contains(RampartStage.MASS)) {
            list.add(this.multiMassProcess);
        }

        if (this.stages == null || this.stages.isEmpty() || this.stages.contains(RampartStage.AMP)) {
            list.add(this.ampProcess);
        }

        /*if (this.stages == null || this.stages.isEmpty()|| this.stages.contains(RampartStage.ANALYSE)) {
            list.add(this.analyserProcess);
        } */

        /*if (this.stages == null || this.stages.isEmpty()|| this.stages.contains(RampartStage.REPORT)) {
            list.add(this.reportProcess);
        } */


        return list;
    }

    @Override
    public List<ConanParameter> getAllRequiredParameters() {
        List<ConanParameter> params = new ArrayList<ConanParameter>();

        if (this.stages == null || this.stages.contains(RampartStage.QT)) {
            params.addAll(new QTParams().getConanParameters());
        }

        if (this.stages == null || this.stages.contains(RampartStage.MASS)) {
            params.addAll(new MultiMassParams().getConanParameters());
        }

        if (this.stages == null || this.stages.contains(RampartStage.AMP)) {
            params.addAll(new AmpParams().getConanParameters());
        }

        /*if (this.stages == null || this.stages.contains(RampartStage.ANALYSE)) {
            params.addAll(new LengthAnalysisParams().getConanParameters());
        }

        if (this.stages == null || this.stages.contains(RampartStage.REPORT)) {
            params.addAll(new ReportParams().getConanParameters());
        } */

        return params;
    }
}
