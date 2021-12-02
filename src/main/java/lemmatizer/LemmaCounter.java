package lemmatizer;

import org.apache.lucene.morphology.LuceneMorphology;

import java.util.*;
import java.util.stream.Collectors;

    public class LemmaCounter {
        LuceneMorphology luceneMorphology;

        public LemmaCounter(LuceneMorphology luceneMorphology) {
            this.luceneMorphology = luceneMorphology;
        }

        public Map<String, Integer> countLemmas(String text) {
            Map<String, Integer> lemma2count = new TreeMap<>();
            String[] words = text.replaceAll("\\p{Punct}", " ").split("\\s");
            List<String> notionalWords = getListOfNotionalWords(words);
            notionalWords.forEach(s -> {
                if (lemma2count.containsKey(s)) {
                    lemma2count.computeIfPresent(s, (key, value) -> value + 1);
                } else {
                    lemma2count.put(s, 1);
                }
            });
            return lemma2count;
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
