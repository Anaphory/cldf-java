package de.tuebingen.sfs.cldfjava.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.tuebingen.sfs.cldfjava.io.PString;

public class CLDFCognateSet<C> {
    // TODO: use this to model cognate set objects as specified at
    // https://github.com/cldf/cldf/blob/master/components/cognatesets/CognatesetTable-metadata.json
    C cogsetID;
    String description;
    List<String> sources;
    Map<String, PString> properties; // to store additional info and remaining properties
    // NOTE: not necessary to create these objects if no additional cognate set
    // information beyond bare IDs is in the database

    public CLDFCognateSet(C id) {
        cogsetID = id;
        description = "";
        sources = new ArrayList<>();
    }

    public C getCogsetID() {
        return cogsetID;
    }

    public void setCogsetID(C cogsetID) {
        this.cogsetID = cogsetID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public Map<String, PString> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, PString> properties) {
        this.properties = properties;
    }
}