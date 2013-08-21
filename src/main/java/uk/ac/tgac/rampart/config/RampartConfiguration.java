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
package uk.ac.tgac.rampart.config;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import uk.ac.tgac.conan.core.data.Library;
import uk.ac.tgac.conan.core.data.Organism;
import uk.ac.tgac.conan.core.util.XmlHelper;
import uk.ac.tgac.rampart.tool.pipeline.amp.AmpArgs;
import uk.ac.tgac.rampart.tool.process.mass.MassArgs;
import uk.ac.tgac.rampart.tool.process.mecq.MecqArgs;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class RampartConfiguration implements Serializable {


    // **** Xml Config Keys ****
    public static final String KEY_ELEM_AUTHOR          = "author";
    public static final String KEY_ELEM_COLLABORATOR    = "collaborator";
    public static final String KEY_ELEM_INSTITUTION     = "institution";
    public static final String KEY_ELEM_TITLE           = "title";
    public static final String KEY_ELEM_DESCRIPTION     = "description";
    public static final String KEY_ELEM_ORGANISM        = "organism";
    public static final String KEY_ELEM_LIBRARIES       = "libraries";
    public static final String KEY_ELEM_PIPELINE        = "pipeline";
    public static final String KEY_ELEM_MECQ            = "mecq";
    public static final String KEY_ELEM_MASS            = "mass";
    public static final String KEY_ELEM_AMP             = "amp";


    // **** Class vars ****
    private String author;
    private String collaborator;
    private String institution;
    private String title;
    private String description;
    private Organism organism;
    private List<Library> libs;
    private MecqArgs mecqSettings;
    private MassArgs massSettings;
    private AmpArgs ampSettings;

    private File file;
	
	public RampartConfiguration() {
        this.author = "Rampart";
        this.collaborator = "";
        this.institution = "";
        this.title = "Rampart Assembly";
        this.description = "";
        this.organism = null;
        this.libs = new ArrayList<Library>();
        this.mecqSettings = null;
        this.massSettings = null;
        this.ampSettings = null;

        this.file = null;
    }

    public RampartConfiguration(File configFile, File outputDir) throws IOException {

        // Set defaults
        this();

        // Record which file was used to create this object.
        this.file = configFile;

        // Create an object which understand the rampart job directory structure
        RampartJobFileStructure jobFS = new RampartJobFileStructure(outputDir);

        // Create job prefix
        String jobPrefix = createJobPrefix();

        // Get a document builder factory
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        try {
            // Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            // parse using builder to get DOM representation of the XML file
            Document dom = db.parse(configFile);

            // Get the root element
            Element root = dom.getDocumentElement();

            // Process all known elements
            this.author = XmlHelper.getTextValue(root, KEY_ELEM_AUTHOR);
            this.institution = XmlHelper.getTextValue(root, KEY_ELEM_INSTITUTION);
            this.collaborator = XmlHelper.getTextValue(root, KEY_ELEM_COLLABORATOR);
            this.title = XmlHelper.getTextValue(root, KEY_ELEM_TITLE);
            this.description = XmlHelper.getTextValue(root, KEY_ELEM_DESCRIPTION);

            // Organism
            Element organismElement = (Element)XmlHelper.getDistinctElementByName(root, KEY_ELEM_ORGANISM);
            this.organism = organismElement == null ? null : new Organism(organismElement);

            // All libraries
            NodeList libNodes = root.getElementsByTagName(KEY_ELEM_LIBRARIES);
            for(int i = 0; i < libNodes.getLength(); i++) {
                this.libs.add(new Library((Element)libNodes.item(i)));
            }

            Element pipelineElement = (Element)XmlHelper.getDistinctElementByName(root, KEY_ELEM_PIPELINE);

            // MECQ
            Element mecqElement = (Element)XmlHelper.getDistinctElementByName(pipelineElement, KEY_ELEM_MECQ);
            this.mecqSettings = mecqElement == null ? null :
                    new MecqArgs(
                            mecqElement,
                            jobFS.getMeqcDir(),
                            jobPrefix + "-mecq",
                            this.libs);

            // MASS
            Element massElement = (Element)XmlHelper.getDistinctElementByName(pipelineElement, KEY_ELEM_MASS);
            this.massSettings = massElement == null ? null :
                    new MassArgs(
                            massElement,
                            jobFS.getMassDir(),
                            jobPrefix + "-mass",
                            this.libs,
                            this.mecqSettings.getEqcArgList(),
                            this.organism);

            // AMP
            Element ampElement = (Element)XmlHelper.getDistinctElementByName(pipelineElement, KEY_ELEM_AMP);
            this.ampSettings = ampElement == null ? null :
                    new AmpArgs(
                            ampElement,
                            jobFS.getAmpDir(),
                            jobPrefix + "-amp",
                            jobFS.getMassOutFile(),
                            this.libs,
                            this.mecqSettings.getEqcArgList(),
                            this.organism);

        }catch(ParserConfigurationException pce) {
            throw new IOException(pce);
        }catch(SAXException se) {
            throw new IOException(se);
        }
    }


    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCollaborator() {
        return collaborator;
    }

    public void setCollaborator(String collaborator) {
        this.collaborator = collaborator;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Organism getOrganism() {
        return organism;
    }

    public void setOrganism(Organism organism) {
        this.organism = organism;
    }

    public List<Library> getLibs() {
        return libs;
    }

    public void setLibs(List<Library> libs) {
        this.libs = libs;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MecqArgs getMecqSettings() {
        return mecqSettings;
    }

    public void setMecqSettings(MecqArgs mecqSettings) {
        this.mecqSettings = mecqSettings;
    }

    public MassArgs getMassSettings() {
        return massSettings;
    }

    public void setMassSettings(MassArgs massSettings) {
        this.massSettings = massSettings;
    }

    public AmpArgs getAmpSettings() {
        return ampSettings;
    }

    public void setAmpSettings(AmpArgs ampSettings) {
        this.ampSettings = ampSettings;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }


    protected final String createJobPrefix() {
        Format formatter = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String dateTime = formatter.format(new Date());
        String jobPrefix = "rampart-" + dateTime;

        return jobPrefix;
    }

}
