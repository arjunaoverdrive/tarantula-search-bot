package org.igor.klimov.app.lemmatizer;

import org.apache.log4j.Logger;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RussianLemmaCounter extends LemmaCounter {
    private final RussianLuceneMorphology luceneMorphology = new RussianLuceneMorphology();
    private static final Logger LOGGER = Logger.getLogger(RussianLemmaCounter.class);
    private static final String WORD_PATTERN = "[а-я]+\\|\\w\\s[А-Я]+\\s\\D+";


    public RussianLemmaCounter() throws IOException {
    }

    @Override
    public String getBasicForm(String s) {
        String basicForm = null;
        try {
            if (luceneMorphology.checkString(s) && s.length() > 2) {
                basicForm = luceneMorphology.getNormalForms(s).get(0);
            }
        }catch (Exception e){
            LOGGER.warn(e);
        }
        return basicForm;
    }


    @Override
    List<String> getListOfNotionalWords(String[] words) {
        return Arrays.stream(words)
                .map(String::toLowerCase)
                .map(s -> s.replaceAll("[^а-я]", ""))
                .filter(s -> !s.isBlank() && !(s.length() == 1))
                .map(s -> luceneMorphology.getMorphInfo(s).get(0))
                .filter(s -> s.matches(WORD_PATTERN))
                .map(s -> luceneMorphology.getNormalForms(s.substring(0, s.indexOf('|'))).get(0))
                .filter(luceneMorphology::checkString)
                .collect(Collectors.toList());
    }

}
