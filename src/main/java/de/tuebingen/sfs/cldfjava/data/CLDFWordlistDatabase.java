package de.tuebingen.sfs.cldfjava.data;

import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * Represents a CLDF database in an object-oriented fashion. Created using CLDFImport.
 *
 * @author jkaparina
 */
public class CLDFWordlistDatabase<F, J, C> {
	public String currentPath;
	//maps as defined by the relevant parts of the CLDF specification (the wordlist module, plus the inherited more general structure)
	//generally, table rows are modeled as objects, whereas properties (single fields) are stored using elementary types
	Map<F, CLDFForm<F>> idToForm; //contents of form table (using integer IDs from typically first column)
	Map<String, CLDFLanguage> langIDToLang; //from foreign key into language table
	Map<String, CLDFParameter> paramIDToParam; //from foreign key (concept ID) into parameters table (typically concepts.csv)
	List<String> langIDs; // store langIDs as ordered list to facilitate indexing
	Map<String, Map<String, List<CLDFForm<F>>>> formsByLanguageByParamID;
	Map<String, List<CLDFForm<F>>> formsByLanguage;

	//TODO: is it really needed?
	Map<J, CLDFCognateJudgement<F, J, C>> cognateIDToCognate; //cognateID to cognate object
	Map<C, CLDFCognateSet<C>> cogsetIDToCogset; //only fill this if in separate table, store within CLDFForm if it's just cognate set IDs
	List<String[]> exceptions;

	public CLDFWordlistDatabase() {
		this.langIDToLang = new HashMap<>();
		this.paramIDToParam = new HashMap<>();
		this.idToForm = new HashMap<>();
		this.cognateIDToCognate = new HashMap<>();
		this.langIDs = new ArrayList<>();
	}

	public CLDFWordlistDatabase(
	        Map<F, CLDFForm<F>> idToForm,
	        Map<String, CLDFLanguage> langIDToLang,
	        Map<String, CLDFParameter> paramIDToParam,
	        Map<J, CLDFCognateJudgement<F, J, C>> cognateIDToCognate,
	        Map<C, CLDFCognateSet<C>> cogsetIDToCogset) {
		this.idToForm = idToForm;
		this.langIDToLang = langIDToLang;
		this.paramIDToParam = paramIDToParam;
		this.cognateIDToCognate = cognateIDToCognate;
		this.cogsetIDToCogset = cogsetIDToCogset;
		this.langIDs = new ArrayList<>(langIDToLang.keySet());
	}

    public List<String[]> getExceptions() {
        return this.exceptions;
    }

    public void setExceptions(List<String[]> exceptions) {
        this.exceptions = exceptions;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void setCurrentPath(String path) {
        currentPath = path;
    }

    public Map<F, CLDFForm<F>> getFormsMap() {
        return idToForm;
    }

    public Map<String, CLDFLanguage> getLanguageMap() {
        return langIDToLang;
    }

    public Map<String, CLDFParameter> getConceptMap() {
        return paramIDToParam;
    }

	public List<String> getLangIDs() {
		return langIDs;
	}


    public Map<C, Set<F>> getCogsetToCognates() {
        Map<C, Set<F>> cognateSets = new HashMap<>();

        for (Entry<J, CLDFCognateJudgement<F, J, C>> entry : cognateIDToCognate.entrySet()) {
            J cognateID = entry.getKey();
            C cogsetID = cognateIDToCognate.get(cognateID).getCognatesetReference();
            F formID = entry.getValue().getFormReference();

            cognateSets.putIfAbsent(cogsetID, new HashSet<>());
            cognateSets.get(cogsetID).add(formID);
        }

        return cognateSets;
    }

    public List<String> listLanguageISOs() {
        ArrayList<String> isoCodes = new ArrayList<String>(langIDToLang.size());
        for (String langID : langIDToLang.keySet()) {
            isoCodes.add(langIDToLang.get(langID).getIso());
        }
        return isoCodes;
    }

    public List<F> listFormIdsForLangId(String langID) {
        List<F> formIDs = new LinkedList<F>();
        for (F formID : idToForm.keySet()) {
            if (langID.equals(idToForm.get(formID).getLangID()))
                formIDs.add(formID);
        }
        return formIDs;
    }

    public String searchLangIdForIsoCode(String isoCode) {
        for (CLDFLanguage lang : langIDToLang.values()) {
            if (isoCode.equals(lang.iso)) {
                return lang.langID;
            }
        }
        return null;
    }


	public CLDFForm<F> getRandomFormForLanguage(String langID) {
		if (formsByLanguage == null) {
			cacheFormsByLanguage();
		}
		List<CLDFForm<F>> allFormsOfTargetLanguage = formsByLanguage.get(langID);

		CLDFForm<F> randomForm;
		try {
			int randomIndex = (int) (Math.random() * allFormsOfTargetLanguage.size());
			randomForm = allFormsOfTargetLanguage.get(randomIndex);
		} catch (Exception e) {
			randomForm = null; // return null if something goes wrong
		}
		return randomForm;
	}

	public void cacheFormsByLanguage() {
		formsByLanguage = new HashMap<>();
		for (CLDFForm<F> form : idToForm.values()) {
			String langID = form.getLangID();
			if (formsByLanguage.containsKey(langID)) {
				List<CLDFForm<F>> forms = formsByLanguage.get(langID);
				forms.add(form);
			} else {
				List<CLDFForm<F>> forms = new ArrayList<>();
				forms.add(form);
				formsByLanguage.put(langID, forms);
			}
		}
	}

        public Map<String, List<CLDFForm<F>>> getFormsByLanguageByParamID(String paramID) {
            if (formsByLanguageByParamID == null) {
                formsByLanguageByParamID = new HashMap<>();
                for (CLDFForm<F> form : idToForm.values()) {
                    String langID = form.getLangID();
                    List<String> localParamIDs = form.getParamID();
                    for (String localParamID : localParamIDs) {
                        if (formsByLanguageByParamID.containsKey(localParamID)) {
                            Map<String, List<CLDFForm<F>>> formsByLang = formsByLanguageByParamID.get(localParamID);
                            if (formsByLang.containsKey(langID)) {
                                List<CLDFForm<F>> forms = formsByLang.get(langID);
                                forms.add(form);
                            } else {
                                List<CLDFForm<F>> forms = new ArrayList<>();
                                forms.add(form);
                                formsByLang.put(langID, forms);
                            }
                        } else {
                            List<CLDFForm<F>> forms = new ArrayList<>();
                            forms.add(form);
                            Map<String, List<CLDFForm<F>>> formsByLang = new HashMap<>();
                            formsByLang.put(langID, forms);
                            formsByLanguageByParamID.put(localParamID, formsByLang);
                        }
                    }
                }
            }

            return formsByLanguageByParamID.get(paramID);
        }

	public List<CLDFForm<F>> getFormsByParamID(String paramID) {
		Map<String, List<CLDFForm<F>>> conceptMapByLanguage = getFormsByLanguageByParamID(paramID);
		List<List<CLDFForm<F>>> conceptsByLanguage = new ArrayList<>(conceptMapByLanguage.values());
		// "flatten" List of Lists to one List containing all values
		return conceptsByLanguage.stream()
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}
}