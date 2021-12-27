package lemmatizer;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LemmaCounter {
    LuceneMorphology luceneMorphology;

    public LemmaCounter() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> countLemmas(String text) {
        Map<String, Integer> lemma2count = new TreeMap<>();
        String[] words = text.replaceAll("\\p{Punct}", " ").split("\\s");
        List<String> notionalWords = getListOfNotionalWords(words);
        notionalWords.forEach(s -> {
            lemma2count.compute(s, (k, v) -> (v == null) ? 1 : v + 1);
        });
        return lemma2count;
    }

    public String getBasicForm(String s) {
        String basicForm = null;
        if (luceneMorphology.checkString(s) && s.length() > 2) {
            basicForm = luceneMorphology.getNormalForms(s).get(0);
        }
        return basicForm;
    }

    private List<String> getListOfNotionalWords(String[] words) {
        return Arrays.stream(words)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .map(s -> s.replaceAll("[^а-я]", ""))
                .filter(s -> !s.isBlank() && !(s.length() == 1))
                .map(s -> luceneMorphology.getMorphInfo(s).get(0))
                .filter(s -> s.matches("[а-я]+\\|\\w\\s[А-Я]+\\s\\D+"))
                .map(s -> luceneMorphology.getNormalForms(s.substring(0, s.indexOf('|'))).get(0))
                .filter(s -> luceneMorphology.checkString(s))
                .collect(Collectors.toList());
    }
}
