package org.arjunaoverdrive.app.lemmatizer;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class LemmaCounter {

    private static final String PUNCTUATION = "[^A-zА-я_]";
    abstract List<String> getListOfNotionalWords(String[] words);
    public abstract String getBasicForm(String s);

    public Map<String, Integer> countLemmas(String text) {
        Map<String, Integer> lemma2count = new TreeMap<>();
        String[] words = text
                .replaceAll(PUNCTUATION, " ")
                .split("\\s");
        List<String> notionalWords = getListOfNotionalWords(words);
        notionalWords.forEach(s ->
            lemma2count.compute(s, (k, v) -> (v == null) ? 1 : v + 1)
        );
        return lemma2count;
    }
}
