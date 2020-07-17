package de.tuebingen.sfs.cldfjava.data;

import java.util.HashMap;
import java.util.Map;

public class CLDFParameter {
	String paramID; //the foreign key used by the rest of the database
	String name;
	String concepticonID;
	String concepticon;
	String semField;
	Map<String,String> properties; //to store additional info
	
	public CLDFParameter() {
		paramID = "";
		name = "";
		concepticonID = "";
		concepticon="";
		semField="";
		properties = new HashMap<>();
	}

	public void setSemanticField(String semField) {
		this.semField=semField;
	}
	public String getSemanticField() {
		return semField;
	}
	public void setConcepticon(String concepticon) {
		this.concepticon = concepticon;
	}
	public String getConcepticon() {
		return this.concepticon;
	}
	public Map<String, String> getProperties() {
		return properties;
	}
	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
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