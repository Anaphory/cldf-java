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
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

/**
 * This class provides functionality to load a CLDF database into a CLDFDatabase
 * object (see structure and interface there).
 * 
 * The module we want to fully support is described here:
 * https://github.com/cldf/cldf/tree/master/modules/Wordlist Test cases are
 * src/test/resources/lexirumah-2.0 and src/test/resources/northeuralex-0.9. We
 * should first read the metadata JSON file, and adapt the parser for the other
 * files to the CSV dialect description (see https://github.com/cldf/cldf)
 */
public class CLDFImport {

    /**
     * A CellReader is an interface for getting a PString (a cell value annotated
     * with the column's data type) from a row. The interface permits virtual
     * columns (by making `translate` a constant function) as well as other
     * conversions.
     *
     */
    interface CellReader {
        abstract PString translate(CSVRecord r);
    }

    static Set<String> languages;
    static Set<String> concepts;
    static Map<String, Integer> originalFormIDs;
    static List<String[]> exceptions;

    /**
     * Load a word list from a folder, by taking the first JSON metadata file.
     * 
     * @deprecated Explicitly load a sepecific metadata file instead.
     * 
     * @param cldfDirName a directory containing a CLDF wordlist metadata JSON file
     * @return CLDFWordlistDatabase
     * @throws CLDFParseError
     * @throws IOException
     */
    @Deprecated
    public static CLDFWordlistDatabase<Integer, String, String> loadDatabase(String cldfDirName)
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

    /**
     * Load a word list according to a JSON metadata file.
     * 
     * @param json the path to a metadata JSON file
     * @return CLDFWordlistDatabase
     * @throws CLDFParseError
     * @throws IOException
     */
    public static CLDFWordlistDatabase<Integer, String, String> loadDatabaseMetadata(File json)
            throws IOException, CLDFParseError {
        // TODO: Using global variables as temporary storage isn't beautiful.
        exceptions = new ArrayList<>();
        languages = new HashSet<>();
        concepts = new HashSet<>();
        originalFormIDs = new HashMap<>();

        URL context = json.toURI().toURL();
        byte[] mapData = Files.readAllBytes(Paths.get(json.getAbsolutePath()));
        JsonNode root = new ObjectMapper().readTree(mapData);
        String moduleType = root.get("dc:conformsTo").asText().split("#")[1];

        if (!moduleType.equals("Wordlist")) {
            throw new CLDFParseError("Expected Wordlist, found " + root.get("dc:conformsTo").asText());
        }

        // Extract the Wordlist module: Inspect all tables of the module.
        JsonNode tables = root.get("tables");
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
        // Retrieve all values we understand from the tables we care about

        // FormTable
        Map<Integer, CLDFForm<Integer>> idToForm;
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
            // Sigh, all we know about concepts are the entries in the FormTable's
            // parameterReference. Turn those into minimal CLDFParameter objects.
            // OR INSTEAD, throw an error and tell the user to create a ParameterTable with
            // lexedata, which can also guess some Concepticon connections.
            for (String concept : concepts) {
                paramIDToParam.put(concept, new CLDFParameter(concept));
            }
        }

        // CognateTable, containing judgements
        Map<String, CLDFCognateJudgement<Integer, String, String>> cognateIDToCognate = new HashMap<>();
        JsonNode cognateTable = tableTypes.get("CognateTable");
        if (cognateTable != null) {
            URL url = new URL(context, cognateTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            cognateIDToCognate = readCognateCsv(table, cognateTable);
        } else {
            // Populating the judgements map only happens if there is a separate file for
            // that.
            // If there isn't one, do nothing. In particular, do not fall back to reading
            // cognate judgements from the FormTable's cognatesetReference, like it was
            // common in the early days of CLDF.
        }

        // CognatesetTable
        Map<String, CLDFCognateSet<String>> cogSetIDToCogset = new HashMap<>();
        JsonNode cognateSetTable = tableTypes.get("CognatesetTable");
        if (cognateSetTable != null) {
            URL url = new URL(context, cognateSetTable.get("url").asText());
            InputStream table = url.openConnection().getInputStream();
            cogSetIDToCogset = readCognatesetCsv(table, cognateSetTable);
        } else {
            // Populating the Cognateset map only happens if there is a separate file for
            // that.
            // This is probably the table we need the least.
        }

        // BorrowingTable
        JsonNode borrowingTableIndex = tableTypes.get("BorrowingTable");
        // We are not actually using this table yet, but know ye that it exists! And it
        // could be useful in Etinen, if we understand the CLDF specs for it and find an
        // example to test with.

        CLDFWordlistDatabase<Integer, String, String> database = new CLDFWordlistDatabase<Integer, String, String>(
                idToForm, langIDToLang, paramIDToParam, cognateIDToCognate, cogSetIDToCogset);
        database.currentPath = json.getParent();

        database.setExceptions(exceptions);
        return database;
    }

    /**
     * Load a single CLDF table into memory as List.
     * 
     * @param stream the stream to read the table from, eg. from opening a file
     * @param table  the CLDF table description in JSON
     * @return tableContent a list of table rows, each mapping properties (falling
     *         back to column names) to PString.
     * @throws IOException
     */
    public static List<Map<String, PString>> readTable(InputStream stream, JsonNode table) throws IOException {
        Builder dialect = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true);
        if (table.get("dialect") != null) {
            // TODO: understand the table dialect, and adjust the format description
            // accordingly
        }
        CSVParser parser = CSVParser.parse(stream, StandardCharsets.UTF_8, dialect.build());

        // Understand the table schema
        List<String> column_headers = parser.getHeaderNames();
        Iterator<JsonNode> column_spec = table.get("tableSchema").get("columns").elements();
        Map<String, CellReader> functions = new HashMap<>();
        while (column_spec.hasNext()) {
            JsonNode column = column_spec.next();
            String name = column.get("name").asText();
            Integer position = column_headers.indexOf(name);
            String property = null;
            try {
                property = column.get("propertyUrl").asText();
                property = property.split("#")[1];
            } catch (IndexOutOfBoundsException e) {
                // The second line got skipped. This is okay.
            } catch (NullPointerException e) {
                property = name;
            }

            // Parse the data type, and set a value mapper accordingly.
            JsonNode separator = column.get("separator");
            String datatype;
            try {
                datatype = column.get("datatype").asText();
            } catch (NullPointerException e) {
                datatype = "string";
            }
            // TODO: If there's a valueUrl, it should be used as string template.
            // TODO: We could have a virtual column, which would ask for a constant
            // CellReader.
            // TODO: Do we need to support integers?
            if (datatype == "double") {
                functions.put(property, r -> new PString(r.get(position), false));
            } else if (separator == null) {
                functions.put(property, r -> new PString(r.get(position), null));
            } else {
                functions.put(property, r -> new PString(r.get(position), separator.asText()));
            }
        }

        // Read the individual rows, and map them.
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
     * Load a FormTable into CLDFForm objects.
     * 
     * Use consecutive integers as IDs. The original ids of forms will be stored in
     * a temporary mapping.
     * 
     * @param stream
     * @param table  The JSON entry describing the table (has key "tableSchema", and
     *               maybe others.)
     * @return a mapping of INTEGERS (not Form IDs!) to CLDFForms
     * @throws IOException
     */
    public static Map<Integer, CLDFForm<Integer>> readFormCsv(InputStream stream, JsonNode table) throws IOException {
        Map<Integer, CLDFForm<Integer>> formTable = new HashMap<>();
        int i = -1;
        for (Map<String, PString> row : readTable(stream, table)) {
            originalFormIDs.put(row.remove("id").toString(), ++i);

            CLDFForm<Integer> formEntry = new CLDFForm<Integer>(i, row.remove("languageReference").toString(),
                    row.remove("parameterReference").toStringList(), row.remove("form").toString());

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
            formTable.put(i, formEntry);

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
     * Load a LanguageTable into CLDFLanguage objects.
     * 
     * @param stream
     * @param table  The JSON entry describing the table (has key "tableSchema", and
     *               maybe others.)
     * @return a mapping of Language IDs (assumed to be strings) to CLDFLanguages
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
                languageEntry.setLongitude((float) row.remove("longitude").toDouble());
            } catch (NullPointerException e) {
            }
            try {
                languageEntry.setLatitude((float) row.remove("latitude").toDouble());
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
     * Load a ParameterTable into CLDFParameter objects, which describe concepts.
     * 
     * @param stream
     * @param table  The JSON entry describing the table (has key "tableSchema", and
     *               maybe others.)
     * @return a mapping of Parameter IDs (assumed to be strings) to CLDFParameter
     *         objects
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
     * Load a CognateTable into CLDFCognateJudgement objects.
     * 
     * @param stream
     * @param table  The JSON entry describing the table (has key "tableSchema", and
     *               maybe others.)
     * @return A mapping of Cognate IDs (assumed to be strings) to
     *         CLDFCognateJudgement objects
     * @throws IOException
     */
    public static Map<String, CLDFCognateJudgement<Integer, String, String>> readCognateCsv(InputStream stream,
            JsonNode table) throws IOException {
        Map<String, CLDFCognateJudgement<Integer, String, String>> cognateTable = new HashMap<>();
        for (Map<String, PString> row : readTable(stream, table)) {

            CLDFCognateJudgement<Integer, String, String> judgement = new CLDFCognateJudgement<Integer, String, String>(
                    row.remove("id").toString(),
                    originalFormIDs.get(row.remove("formReference").toString()),
                    row.remove("cognatesetReference").toString());

            // for the remaining columns, put them into a property map
            judgement.setProperties(row);
            // mapping object and its id
            cognateTable.put(judgement.getCognateID(), judgement);
        }
        return cognateTable;
    }

    /**
     * Load a CognatesetTable into CLDFCognateSet objects.
     * 
     * @param stream
     * @param table  The JSON entry describing the table (has key "tableSchema", and
     *               maybe others.)
     * @return A mapping of Cognateset IDs (assumed to be strings) to CLDFCognateSet
     *         objects
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
