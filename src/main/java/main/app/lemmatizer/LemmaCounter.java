package main.app.lemmatizer;

import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LemmaCounter {
    private RussianLuceneMorphology luceneMorphology;
    private EnglishLuceneMorphology englishLuceneMorphology;

    public LemmaCounter() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
        this.englishLuceneMorphology = new EnglishLuceneMorphology();
    }


    public Map<String, Integer> countLemmas(String text) {
        Map<String, Integer> lemma2count = new TreeMap<>();
        String[] words = text.replaceAll("\\p{Punct}", " ").split("\\s");
        List<String> notionalWords = getListOfNotionalWords(words);
        if (notionalWords.size() == 0) {
            notionalWords = getListOfNotionalWordsEng(words);
        }
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

    private List<String> getListOfNotionalWordsEng(String[] words) {
        List<String> list = new ArrayList<>();
        for (String s : words) {
            String toLowerCase = s.toLowerCase();
            String replaceAll = toLowerCase.replaceAll("[^a-z]", "0");
            try {
                if (!replaceAll.contains("0") && !replaceAll.equals("not")
                        && replaceAll.length() > 2) {
                    String s1 = englishLuceneMorphology.getMorphInfo(replaceAll).get(0);
                    String s2 = englishLuceneMorphology.getNormalForms(s1.substring(0, s1.indexOf('|'))).get(0);
                    if (!s1.contains("PREP") && !s1.contains("CONJ")
                            && !s1.contains("INT") && !s1.contains("PART") && !s1.contains("ARTICLE")) {
                        list.add(s2);
                    }
                }
            }catch (IndexOutOfBoundsException e){
//                System.out.println(s + " " + " ==================\n" + e.getMessage());
            }
        }
        return list;
    }
}
