package de.tuebingen.sfs.cldfjava.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tuebingen.sfs.cldfjava.io.CLDFImport;
import de.tuebingen.sfs.cldfjava.io.PString;

public class CLDFForm<FormID> {
    FormID id; // the id used to reference forms in other tables (e.g. cognates.csv or
               // borrowings.csv)
    String langID; // Language_ID
    List<String> paramID; // Parameter_ID
    String form;
    String origValue; // Value
    String comment;

    String orthography;
    Map<String, PString> properties; // to store additional info, e.g. the original orthography, under their column
                                     // name
    String[] segments;

    public CLDFForm(FormID id, String language, List<String> concepts, String form) {
        this.id = id;
        this.langID = language;
        this.paramID = concepts;
        this.form = form;
        origValue = "";
        comment = "";
        properties = new HashMap<>();
        orthography = "";
    }

    public String[] getSegments() {
        return segments;
    }

    public void setSegments(List<String> segments) {
        this.segments = new String[segments.size()];
        segments.toArray(this.segments);
    }

    public String getOrthography() {
        return orthography;
    }

    public void setOrthography(String orthography) {
        this.orthography = orthography;
    }

    public Map<String, PString> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, PString> row) {
        this.properties = row;
    }

    public FormID getId() {
        return id;
    }

    public void setId(FormID string) {
        this.id = string;
    }

    public String getForm() {
        return form;
    }

    public void setForm(String form) {
        this.form = form;
    }

    public String getOrigValue() {
        return origValue;
    }

    public void setOrigValue(String origValue) {
        this.origValue = origValue;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getLangID() {
        return langID;
    }

    public void setLangID(String langID) {
        this.langID = langID;
    }

    public List<String> getParamID() {
        return paramID;
    }

    public void setParamID(List<String> list) {
        this.paramID = list;
    }

    public String toString() {
        return id + "\t" + form + "\t" + langID + "\t" + paramID + "\t" + properties;
    }

}