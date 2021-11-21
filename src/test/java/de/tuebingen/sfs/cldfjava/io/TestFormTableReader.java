package de.tuebingen.sfs.cldfjava.io;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tuebingen.sfs.cldfjava.data.CLDFForm;

class TestFormTableReader {

    @Test
    void testReadFormTable() throws IOException {
        CLDFImport.exceptions = new ArrayList<>();
        CLDFImport.languages = new HashSet<>();
        CLDFImport.concepts = new HashSet<>();
        CLDFImport.originalFormIDs = new HashMap<>();

        Map<Integer, CLDFForm<Integer>> result = CLDFImport.readFormCsv(new ByteArrayInputStream("""
                ID,Language_ID,Parameter_ID,Form
                1,fra,one,un""".getBytes()), new ObjectMapper().readTree("""
                {"tableSchema": {"columns": [
                {"name": "ID", "propertyUrl": "id"},
                {"name": "Language_ID", "propertyUrl": "languageReference"},
                {"name": "Parameter_ID", "propertyUrl": "parameterReference"},
                {"name": "Form", "propertyUrl": "form"}
                ]}}"""));

        assertEquals(1, CLDFImport.originalFormIDs.size());
        assertEquals(
                Collections.singletonMap("1", 0),
                CLDFImport.originalFormIDs);
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getId());
        assertEquals("un", result.get(0).getForm());
        assertEquals("fra", result.get(0).getLangID());
        assertEquals(Arrays.asList(new String[] { "one" }), result.get(0).getParamID());
    }
}
