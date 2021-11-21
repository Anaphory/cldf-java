package de.tuebingen.sfs.cldfjava.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tuebingen.sfs.cldfjava.data.CLDFCognateJudgement;
import de.tuebingen.sfs.cldfjava.data.CLDFCognateSet;
import de.tuebingen.sfs.cldfjava.data.CLDFForm;
import de.tuebingen.sfs.cldfjava.data.CLDFLanguage;
import de.tuebingen.sfs.cldfjava.data.CLDFParameter;
import de.tuebingen.sfs.cldfjava.data.CLDFWordlistDatabase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class CLDFImport {

    interface CellReader {
        abstract PString translate(CSVRecord r);
    }

    static Set<String> languages;
    static Set<String> concepts;
    static List<String[]> exceptions;

    /**
     * TODO: This should build a CLDFDatabase object (see structure and interface
     * there) from a directory with CLDF files. The module we want to fully support
     * is described here: https://github.com/cldf/cldf/tree/master/modules/Wordlist
     * Test cases are src/test/resources/lexirumah-2.0 and
     * src/test/resources/northeuralex-0.9. We should first read the metadata JSON
     * file, and adapt the parser for the other files to the CSV dialect description
     * (see https://github.com/cldf/cldf) The functionality of importAtomsFromFile
     * (and retrieveAtoms) should then be moved to LexicalAtomExtractor (see there)
     *
     * @param cldfDirName
     * @return
     * @throws CLDFParseError
     * @throws IOException
     */
    public static CLDFWordlistDatabase<String, String, String> loadDatabase(String cldfDirName)
            throws IOException, CLDFParseError {
        File path = new File(cldfDirName);
        // possible json files in the given folder
        File[] possibleJsons = path.listFiles((File dir, String name) -> name.endsWith("metadata.json"));

        File json;
        if (possibleJsons.length == 0) { // if 0, no json found in the folder
            throw new Error("folder");
        } else {
            // Filter for a Wordlist module
            json = possibleJsons[0];
        }

        return loadDatabaseMetadata(json);
    }

    public static CLDFWordlistDatabase<String, String, String> loadDatabaseMetadata(File json)
            throws IOException, CLDFParseError {
        exceptions = new ArrayList<>();
        languages = new HashSet<>();
        concepts = new HashSet<>();

        URL context = json.toURI().toURL();
        byte[] mapData = Files.readAllBytes(Paths.get(json.getAbsolutePath()));
        JsonNode root = new ObjectMapper().readTree(mapData);
        String moduleType = root.get("dc:conformsTo").asText().split("#")[1]; // extracting the module from the link

        if (!moduleType.equals("Wordlist")) {
            throw new CLDFParseError("Expected Wordlist, found " + root.get("dc:conformsTo").asText());
        }

        // extracting the Wordlist module
        JsonNode tables = root.get("tables"); // extracting all tables of the module
        Map<String, JsonNode> tableTypes = new HashMap<>();

        for (JsonNode table : tables) {
            String tableType;
            if (table.get("dc:conformsTo") == null)
                tableType = null; // No table type given, who knows what kind of table that is.
            else {
                try {
                    // TODO: Check that the bit before the '#' points to the CLDF spec.
                    tableType = table.get("dc:conformsTo").asText().split("#")[1];
                } catch (IndexOutOfBoundsException e) {
                    // No CLDF table type given, but maybe still useful.
                    tableType = table.get("dc:conformsTo").asText();
                }
            }
            tableTypes.put(tableType, table);
        }

        // index of a type of table in the list is the one we that we will refer to
        // retrieve all values we understand for the tables we care about

        // FormTable
        Map<String, CLDFForm<String>> idToForm;
        JsonNode formTable = tableTypes.get("FormTable");
        if (formTable != null) {
            URL url = new URL(context, formTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            idToForm = readFormCsv(table, formTable);
        } else {
            throw new CLDFParseError("Wordlist had no FormTable.");
        }

        // LanguageTable
        Map<String, CLDFLanguage> langIDToLang = new HashMap<>();
        JsonNode languageTable = tableTypes.get("LanguageTable");
        if (languageTable != null) {
            URL url = new URL(context, languageTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            langIDToLang = readLanguageCsv(table, languageTable);
        } else {
            // Sigh, all we know about language IDs are the entries in the FormTable's
            // languageReference. Turn those into minimal CLDFLanguage objects.
            // OR INSTEAD, throw an error and tell the user to create a LanguageTable with
            // lexedata, which can also guess some Glottocodes.
            for (String language : languages) {
                langIDToLang.put(language, new CLDFLanguage(language));
            }
        }

        // ParameterTable
        Map<String, CLDFParameter> paramIDToParam = new HashMap<>();
        JsonNode parameterTable = tableTypes.get("ParameterTable");
        if (parameterTable != null) {
            URL url = new URL(context, parameterTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            paramIDToParam = readParameterCsv(table, parameterTable);
        } else {
            // TODO: Sigh, all we know about concepts are the entries in the FormTable's
            // parameterReference. Turn those into minimal CLDFParameter objects.
            // OR INSTEAD, throw an error and tell the user to create a ParameterTable with
            // lexedata, which can also guess some Concepticon connections.
            for (String concept : concepts) {
                paramIDToParam.put(concept, new CLDFParameter(concept));
            }
        }

        // CognateTable, containing judgements
        Map<String, CLDFCognateJudgement<String, String, String>> cognateIDToCognate = new HashMap<>();
        JsonNode cognateTable = tableTypes.get("CognateTable");
        if (cognateTable != null) {
            URL url = new URL(context, cognateTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            cognateIDToCognate = readCognateCsv(table, cognateTable);
        } else {
            // populating the judgements map only happens if there is a separate file for
            // that.
            // If there isn't one, do nothing. In particular, do not fall back to reading
            // cognate judgements from the FormTable, like it was common in the early days
            // of CLDF.
        }

        // CognatesetTable
        Map<String, CLDFCognateSet<String>> cogSetIDToCogset = new HashMap<>();
        JsonNode cognateSetTable = tableTypes.get("CognatesetTable");
        if (cognateSetTable != null) {
            URL url = new URL(context, cognateSetTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            cogSetIDToCogset = readCognatesetCsv(table, cognateSetTable);
        } else {
            // populating Cognateset map only happens if there is a separate file for that.
            // This is probably the table we need the least.
        }

        // BorrowingTable
        JsonNode borrowingTableIndex = tableTypes.get("BorrowingTable");
        // We are not actually using this table yet, but know ye that it exists! And it
        // could be useful in Etinen, if we understand the CLDF specs for it and find an
        // example to test with.

        CLDFWordlistDatabase<String, String, String> database = new CLDFWordlistDatabase<String, String, String>(
                idToForm, langIDToLang, paramIDToParam, cognateIDToCognate, cogSetIDToCogset);
        database.currentPath = json.getParent();

        database.setExceptions(exceptions);
        return database;
    }

    public static List<Map<String, PString>> readTable(InputStream stream, JsonNode table) throws IOException {
        CSVFormat dialect = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build();
        if (table.get("dialect") != null) {
            // TODO: understand the table dialect, and adjust the format description
            // accordingly
        }
        CSVParser parser = CSVParser.parse(stream, StandardCharsets.UTF_8, dialect);

        List<String> column_headers = parser.getHeaderNames();
        Iterator<JsonNode> column_spec = table.get("tableSchema").get("columns").elements();
        Map<String, CellReader> functions = new HashMap<>();
        while (column_spec.hasNext()) {
            JsonNode column = column_spec.next();
            String name = column.get("name").asText();
            Integer position = column_headers.indexOf(name);
            String function = null;
            try {
                function = column.get("propertyUrl").asText();
                function = function.split("#")[1];
            } catch (IndexOutOfBoundsException e) {
                // The second line got skipped. This is okay.
            } catch (NullPointerException e) {
                function = name;
            }
            // TODO: If there's a valueUrl, it should be used as template.
            // TODO: We could have a virtual column, which would ask for a constant fn.
            JsonNode separator = column.get("separator");
            if (separator == null) {
                functions.put(function, r -> new PString(r.get(position), null));
            } else {
                functions.put(function, r -> new PString(r.get(position), separator.asText()));
            }
        }

        List<Map<String, PString>> rows = new ArrayList<>();
        for (CSVRecord row : parser) {
            Map<String, PString> processed_row = new HashMap<>();
            for (String column : functions.keySet()) {
                processed_row.put(column, functions.get(column).translate(row));
            }
            rows.add(processed_row);
        }
        return rows;
    }

    /**
     * A method for reading the Form Table
     *
     * @param path  of the file to read
     * @param table The JSON entry describing the table (has key "tableSchema", and
     *              maybe others.)
     * @return id to Form object map
     * @throws IOException
     */
    public static Map<String, CLDFForm<String>> readFormCsv(InputStream stream, JsonNode table) throws IOException {
        Map<String, CLDFForm<String>> formTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFForm<String> formEntry = new CLDFForm<String>(row.remove("id").toString(),
                    row.remove("languageReference").toString(), row.remove("parameterReference").toStringList(),
                    row.remove("form").toString());

            // settings fields that aren't required by checking whether they exist
            try {
                formEntry.setOrigValue(row.remove("value").toString());
            } catch (NullPointerException e) {
            }
            try {
                formEntry.setComment(row.remove("comment").toString());
            } catch (NullPointerException e) {
            }
            try {
                formEntry.setSegments(row.remove("segments").toStringList());
            } catch (NullPointerException e) {
            }
            // TODO: Orthography is not a standard CLDF column, try more options
            try {
                formEntry.setOrthography(row.remove("orthographic").toString());
            } catch (NullPointerException e) {
            }

            // for the remaining columns, put them into a property map
            formEntry.setProperties(row);
            // mapping object and its id
            formTable.put(formEntry.getId(), formEntry);

            languages.add(formEntry.getLangID());
            concepts.addAll(formEntry.getParamID());
        }
        return formTable;
    }

    private static String familyFromGlottocode(String glottocode) {
        // TODO Where do we store the Glottolog tree again??
        InputStream tree = CLDFImport.class.getResourceAsStream("/glottolog/trees.nwk");
        return "Arawak";
    }

    /**
     * A method for reading the Language Table
     *
     * @param stream          of the file to read
     * @param propertyColumns a map of properties and their columns
     * @return id to Language object map
     * @throws IOException
     */
    public static Map<String, CLDFLanguage> readLanguageCsv(InputStream stream, JsonNode table) throws IOException {
        Map<String, CLDFLanguage> languageTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFLanguage languageEntry = new CLDFLanguage(row.remove("id").toString());

            // settings fields that aren't required by checking whether they exist
            try {
                languageEntry.setIso(row.remove("iso639P3code").toString());
            } catch (NullPointerException e) {
            }

            try {
                languageEntry.setGlottocode(row.remove("glottocode").toString());
            } catch (NullPointerException e) {
            }
            try {
                languageEntry.setName(row.remove("name").toString());
            } catch (NullPointerException e) {
            }
            try {
                languageEntry.setLongitude(row.remove("longitude").toFloat());
            } catch (NullPointerException e) {
            }
            try {
                languageEntry.setLatitude(row.remove("latitude").toFloat());
            } catch (NullPointerException e) {
            }

            // "Family" is not a standard CLDF term.
            try {
                languageEntry.setFamily(row.remove("family").toString());
            } catch (NullPointerException e) {
            }
            if (languageEntry.getFamily() == null && languageEntry.getGlottocode() != null) {
                languageEntry.setFamily(familyFromGlottocode(languageEntry.getGlottocode()));
            }

            // for the remaining columns, put them into a property map
            languageEntry.setProperties(row);
            // mapping object and its id
            languageTable.put(languageEntry.getLangID(), languageEntry);
        }
        return languageTable;
    }

    /**
     * A method for reading the Parameter Table (containing concepts)
     *
     * @param stream          of the file to read
     * @param propertyColumns a map of properties and their columns
     * @return id to concept object map
     * @throws IOException
     */
    public static Map<String, CLDFParameter> readParameterCsv(InputStream stream, JsonNode table) throws IOException {
        Map<String, CLDFParameter> parameterTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFParameter parameterEntry = new CLDFParameter(row.remove("id").toString());

            // settings fields that aren't required by checking whether they exist
            try {
                parameterEntry.setName(row.remove("name").toString());
            } catch (NullPointerException e) {
            }
            try {
                parameterEntry.setConcepticonID(row.remove("concepticonReference").toString());
            } catch (NullPointerException e) {
            }
            // "Concepticon Gloss" is not a standard CLDF term.
            try {
                parameterEntry.setConcepticon(row.remove("name").toString());
                // TODO: if "Concepticon Gloss" does not exist, try to derive from Concepticon
                // ID.
            } catch (NullPointerException e) {
            }

            try {
                // "Semantic Field" is not a standard CLDF term.
                parameterEntry.setSemanticField(row.remove("name").toString());
            } catch (NullPointerException e) {
            }

            // for the remaining columns, put them into a property map
            parameterEntry.setProperties(row);
            // mapping object and its id
            parameterTable.put(parameterEntry.getParamID(), parameterEntry);
        }
        return parameterTable;
    }

    /**
     * A method for reading the CognateTable (containing judgements)
     *
     * @param stream          of the file to read
     * @param propertyColumns a map of properties and their columns
     * @return id to judgement object map
     * @throws IOException
     */
    public static Map<String, CLDFCognateJudgement<String, String, String>> readCognateCsv(InputStream stream,
            JsonNode table) throws IOException {
        Map<String, CLDFCognateJudgement<String, String, String>> cognateTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFCognateJudgement<String, String, String> judgement = new CLDFCognateJudgement<String, String, String>(
                    row.remove("id").toString(), row.remove("formReference").toString(),
                    row.remove("cognatesetReference").toString());

            // for the remaining columns, put them into a property map
            judgement.setProperties(row);
            // mapping object and its id
            cognateTable.put(judgement.getCognateID(), judgement);
        }
        return cognateTable;
    }

    /**
     * A method for reading the CognateTable (containing judgements)
     *
     * @param stream          of the file to read
     * @param propertyColumns a map of properties and their columns
     * @return id to judgement object map
     * @throws IOException
     */
    public static Map<String, CLDFCognateSet<String>> readCognatesetCsv(InputStream stream, JsonNode table)
            throws IOException {
        Map<String, CLDFCognateSet<String>> cogsetTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFCognateSet<String> cogset = new CLDFCognateSet<String>(row.remove("id").toString());
            try {
                cogset.setDescription(row.remove("description").toString());
            } catch (NullPointerException n) {
            }
            try {
                cogset.setSources(row.remove("description").toStringList());
            } catch (NullPointerException n) {
            }

            // for the remaining columns, put them into a property map
            cogset.setProperties(row);
            // mapping object and its id
            cogsetTable.put(cogset.getCogsetID(), cogset);
        }
        return cogsetTable;
    }
}
