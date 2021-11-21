package de.tuebingen.sfs.cldfjava.io;

import java.util.Arrays;
import java.util.List;

/**
 * @author anaphory
 *
 */
public class PString {
    /**
     * The PSTring class contains a CLDF cell value.
     * 
     * Technically, this should somehow map onto CSVW's anyAtomicType (see
     * https://w3c.github.io/csvw/metadata/#built-in-datatypes), but for now it
     * supports only the types we have seen used in CLDF wordlists, namely string,
     * lists of string (when a separator is given), and double (which is used for
     * geocoordinates).
     * 
     */
    private String separator;
    private boolean isNumeric;
    private String rawContent;

    public PString(String s) {
        separator = null;
        rawContent = s;
    }

    public PString(String s, String sep) {
        separator = sep;
        rawContent = s;
    }

    public PString(String s, boolean isInteger) {
        isNumeric = true;
        rawContent = s;
    }

    public String toString() {
        return rawContent;
    }

    public List<String> toStringList() {
        if (separator == null) {
            return Arrays.asList(new String[] { rawContent });
        } else {
            return Arrays.asList(rawContent.split(separator));
        }
    }

    public double toDouble() {
        if (isNumeric) {
            return Double.valueOf(rawContent);
        } else {
            return Double.NaN;
        }
    }
}