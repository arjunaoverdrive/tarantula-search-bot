package search;

import lemmatizer.LemmaCounter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SnippetParser {
    private String query;
    private String html;
    private final LemmaCounter counter;

    public SnippetParser(String query, String html) throws IOException {
        this.query = query;
        this.html = html;
        this.counter = new LemmaCounter();
    }

    private List<String> getWordsList(String str) {
        return Arrays.stream(str.toLowerCase().replaceAll("[^а-я]", " ")
                        .split(" "))
                .filter(s -> s.length() > 2)
                .collect(Collectors.toList());
    }

    private List<WordOnPage> getWordsOnPage() {
        List<WordOnPage> wordOnPageList = new ArrayList<>();
        List<String> words = getWordsList(html);
        String html2lowerCase = html.toLowerCase();
        for (String w : words) {
            WordOnPage word = new WordOnPage();
            word.setWord(w);
            word.setLemma(counter.getBasicForm(w));
            word.setPosition(html2lowerCase.indexOf(w));
            wordOnPageList.add(word);
        }
        return wordOnPageList;
    }

    private List<String> getListOfQueryLemmas() {
        List<String> wordsFromQuery = getWordsList(query);
        List<String> words = new ArrayList<>();
        for (String s : wordsFromQuery) {
            words.add(counter.getBasicForm(s));
        }
        return words;
    }

    private WordOnPage getQueryWordOnPageObject(String lemma) {
        List<WordOnPage> wordsOnPage = getWordsOnPage();
        WordOnPage target = null;
        for (WordOnPage w : wordsOnPage) {
            if (w.getLemma().equalsIgnoreCase(lemma)) {
                target = w;
            }
        }
        return target;
    }

    private List<WordOnPage> getQueryWordsOnPageList() {
        List<String> lemmasFromQuery = getListOfQueryLemmas();
        List<WordOnPage> res = new ArrayList<>();
        for (String lemma : lemmasFromQuery) {
            WordOnPage w = getQueryWordOnPageObject(lemma);
            res.add(w);
        }
        return res;
    }

    private List<WordOnPage> sortByPositionDesc() {
        List<WordOnPage> queryWordsOnPageList = getQueryWordsOnPageList();
        return queryWordsOnPageList.stream()
                .sorted(Comparator.comparing(WordOnPage::getPosition).reversed())
                .collect(Collectors.toList());
    }


    private String addBoldTag() {
        List<WordOnPage> words = sortByPositionDesc();
        String openTag = "<b>";
        String closeTag = "</b>";
        StringBuilder builder = new StringBuilder(html);
        for (WordOnPage w : words) {
            builder.insert(w.getPosition() + w.getWord().length(), closeTag);
            builder.insert(w.getPosition(), openTag);
        }
        return builder.toString();
    }

    String removeExtraTags() {
        String str = addBoldTag();
        String newStr = str.replaceAll("</b> <b>", " ").replaceAll("</b>, <b>", ", ");
        return newStr;
    }

    String shortenLongSnippet(String text) {
        if (text.length() > 500) {
            String[] sentences = text.split("[\\.\\?\\.{3}\\!]");
            StringBuilder builder = new StringBuilder();
            for (String s : sentences) {
                String string2append = s.contains("<b>") || s.contains("</b>") ? s + "..." : "";
                builder.append(string2append);
            }
            return builder.toString();
        }
        return text;
    }
}
