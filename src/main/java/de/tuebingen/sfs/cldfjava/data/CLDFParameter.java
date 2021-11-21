package de.tuebingen.sfs.cldfjava.data;

import java.util.HashMap;
import java.util.Map;

import de.tuebingen.sfs.cldfjava.io.PString;

public class CLDFParameter {
    String paramID; //the foreign key used by the rest of the database
    String name;
    String concepticonID;
    String concepticon;
    String semField;
    Map<String, PString> properties; //to store additional info

    public CLDFParameter(String concept) {
        paramID = concept;
        name = "";
        concepticonID = "";
        concepticon = "";
        semField = "";
        properties = new HashMap<>();
    }

    public String getSemanticField() {
        return semField;
    }

    public void setSemanticField(String semField) {
        this.semField = semField;
    }

    public String getConcepticon() {
        return this.concepticon;
    }

    public void setConcepticon(String concepticon) {
        this.concepticon = concepticon;
    }

    public Map<String, PString> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, PString> more_properties) {
        this.properties = more_properties;
    }

    public String getConcepticonID() {
        return concepticonID;
    }

    public void setConcepticonID(String concepticonID) {
        this.concepticonID = concepticonID;
    }

    public String getParamID() {
        return paramID;
    }

    public void setParamID(String paramID) {
        this.paramID = paramID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return "ID: {" + paramID + "}, Name: {" + name + "}, ConcID: {" + concepticonID + "}, Conc: {" + concepticon + "}, SemField: {" + semField + "}";
    }
}