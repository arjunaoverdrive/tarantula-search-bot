package org.igor.klimov.app.lemmatizer;

import org.apache.log4j.Logger;
import org.apache.lucene.morphology.WrongCharaterException;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EnglishLemmaCounter extends LemmaCounter {
    private final EnglishLuceneMorphology englishLuceneMorphology = new EnglishLuceneMorphology();

    private static final Logger LOGGER = Logger.getLogger(EnglishLemmaCounter.class);


    public EnglishLemmaCounter() throws IOException {
    }

    @Override
    List<String> getListOfNotionalWords(String[] words) {

        List<String> list = new ArrayList<>();
        List<String> filteredStrings = filterStrings(words);

        for (String s : filteredStrings) {
            try {
                String morphInfo = englishLuceneMorphology.getMorphInfo(s).get(0);

                boolean isNotionalPartOfSpeech = !morphInfo.contains("PREP") && !morphInfo.contains("CONJ") &&
                        !morphInfo.contains("INT") && !morphInfo.contains("PART") && !morphInfo.contains("ARTICLE");

                String normalForm =
                        englishLuceneMorphology.getNormalForms(morphInfo.substring(0, morphInfo.indexOf('|'))).get(0);

                if (isNotionalPartOfSpeech) {
                    list.add(normalForm);
                }
            } catch (IndexOutOfBoundsException suppressed) {

            }
        }
        return list;
    }

    private List<String> filterStrings(String[] words) {
        return Arrays.stream(words)
                .map(String::toLowerCase)
                .filter(s -> !s.contains("_"))
                .map(s -> s.replaceAll("[^\\p{Lower}]", "0"))
                .filter(s -> !s.contains("0"))
                .filter(s -> s.length() > 2)
                .filter(s -> !s.equals("not"))
                .collect(Collectors.toList());
    }

    @Override
    public String getBasicForm(String s) {
        String basicForm = null;

        try {
            if (englishLuceneMorphology.checkString(s) && s.length() > 2 && !s.contains("_")) {
                basicForm = englishLuceneMorphology.getNormalForms(s).get(0);
            }
        } catch (WrongCharaterException wce) {
            LOGGER.warn(s);
        }

        return basicForm;
    }
}
