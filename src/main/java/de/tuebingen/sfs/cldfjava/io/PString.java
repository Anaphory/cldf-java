package de.tuebingen.sfs.cldfjava.io;

import java.util.Arrays;
import java.util.List;

public class PString {
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

    public float toFloat() {
        if (isNumeric) {
            return Float.valueOf(rawContent);
        } else {
            return (float) 0.0;
        }
    }
}