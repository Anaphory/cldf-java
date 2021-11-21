package de.tuebingen.sfs.cldfjava.data;

import java.util.Map;

import de.tuebingen.sfs.cldfjava.io.PString;

public class CLDFCognateJudgement<F, J, C> {
    J cognateID;
    F formReference;
    C cognatesetReference;
    Map<String, PString> properties; //to store additional info

    public CLDFCognateJudgement(J id, F formID, C cognatesetID) {
        cognateID = id;
        formReference = formID;
        cognatesetReference = cognatesetID;
    }

    public J getCognateID() {
        return cognateID;
    }

    public void setCognateID(J cognateID) {
        this.cognateID = cognateID;
    }

    public F getFormReference() {
        return formReference;
    }

    public void setFormReference(F formReference) {
        this.formReference = formReference;
    }

    public C getCognatesetReference() {
        return cognatesetReference;
    }

    public void setCognatesetReference(C cognatesetReference) {
        this.cognatesetReference = cognatesetReference;
    }
    
    public Map<String, PString> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, PString> more_properties) {
        this.properties = more_properties;
    }
}