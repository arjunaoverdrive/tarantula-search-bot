package org.igor.klimov.app.lemmatizer;

import org.apache.log4j.Logger;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EnglishLemmaCounter extends LemmaCounter{
    private final EnglishLuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();

    private static final Logger LOGGER = Logger.getLogger(EnglishLemmaCounter.class);


    public EnglishLemmaCounter() throws IOException {
    }

    @Override
    List<String> getListOfNotionalWords(String[] words) {

        List<String> list = new ArrayList<>();

        for (String s : words) {

            String toLowerCase = s.toLowerCase();
            String textString = toLowerCase.replaceAll("[^a-z]", "0");

            try {
                if (!textString.contains("0") && !textString.equals("not")
                        && textString.length() > 2) {

                    String morphInfo = englishLuceneMorphology.getMorphInfo(textString).get(0);
                    String normalForm =
                            englishLuceneMorphology
                                    .getNormalForms(morphInfo.substring(0, morphInfo.indexOf('|'))).get(0);

                    if (!morphInfo.contains("PREP") && !morphInfo.contains("CONJ") &&
                            !morphInfo.contains("INT") && !morphInfo.contains("PART") &&
                            !morphInfo.contains("ARTICLE")) {
                        list.add(normalForm);
                    }
                }
            }catch (IndexOutOfBoundsException suppressed){

            }
        }
        return list;
    }

    @Override
    public String getBasicForm(String s) {
        String basicForm = null;

        try {
            if (englishLuceneMorphology.checkString(s) && s.length() > 2) {
                basicForm = englishLuceneMorphology.getNormalForms(s).get(0);
            }
        }catch (WrongCharaterException wce){
            LOGGER.warn(s);
        }

        return basicForm;
    }
}
