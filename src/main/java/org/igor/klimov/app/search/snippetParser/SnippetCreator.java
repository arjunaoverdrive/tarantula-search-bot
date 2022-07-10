package org.igor.klimov.app.search.snippetParser;

import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.igor.klimov.app.model.Lemma;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SnippetCreator implements SnippetParser {
    private final List<Lemma> queryLemmas;
    private final String html;
    private final LemmaCounter counter;

    private static final Pattern WORD_ON_PAGE_PATTERN = Pattern.compile("\\b[А-яA-z]+\\b");
    private static final Pattern REMOVE_EXTRA_TAGS_PATTERN = Pattern.compile("</b>[\\s\\p{Punct}]?<b>");
    private static final Pattern GET_SHORT_SNIPPET_PATTERN = Pattern.compile("(.*<b>.*</b>.*)\\p{Punct}?");

    public SnippetCreator(List<Lemma> queryLemmas, String html, LemmaCounter counter) {
        this.queryLemmas = queryLemmas;
        this.html = html;
        this.counter = counter;
    }

    @Override
    public String create() {
        return getShortSnippet();
    }

    private String clearContentFromExtraElements() {

        Document document = Jsoup.parse(html, "UTF-8");

        document.getElementsByTag("header").remove();
        document.getElementsByTag("noscript").remove();
        document.getElementsByTag("footer").remove();
        document.getElementsByTag("script").remove();

        return document.wholeText();
    }


    private List<WordOnPage> getWordsOnPageList() {

        String text = clearContentFromExtraElements();

        Matcher matcher = WORD_ON_PAGE_PATTERN.matcher(text);

        List<WordOnPage> wordsFromPage = new ArrayList<>();

        while (matcher.find()) {
            String match = matcher.group().toLowerCase();
            WordOnPage wop = new WordOnPage();

            String lemma = counter.getBasicForm(match);
            if (lemma == null) {
                continue;
            }

            wop.setLemma(lemma);
            wop.setPosition(matcher.start());
            wop.setWord(matcher.group());

            wordsFromPage.add(wop);
        }

        return wordsFromPage;
    }

    private Map<String, List<WordOnPage>> getLemmaToWordOnPageMap() {

        List<WordOnPage> wordsOnPage = getWordsOnPageList();

        Map<String, List<WordOnPage>> lemmaToWordOnPageMap = new TreeMap<>();

        for (WordOnPage w : wordsOnPage) {
            String l = w.getLemma();

            if (lemmaToWordOnPageMap.get(l) == null) {
                List<WordOnPage> lemmaOccurrences = List.of(w);

                lemmaToWordOnPageMap.put(l, lemmaOccurrences);
            } else {
                List<WordOnPage> values = lemmaToWordOnPageMap.get(l);

                ArrayList<WordOnPage> list = new ArrayList<>(values);

                list.add(w);
                lemmaToWordOnPageMap.put(l, list);
            }
        }

        return lemmaToWordOnPageMap;
    }

    private List<WordOnPage> findQueryWordsInListOfWordsOnPage() {

        List<WordOnPage> matches = new ArrayList<>();

        Map<String, List<WordOnPage>> lemmaToWordOnPage = getLemmaToWordOnPageMap();

        for (Lemma l : queryLemmas) {
            List<WordOnPage> occurrenceList = lemmaToWordOnPage.get(l.getLemma());
            if (occurrenceList != null)
                matches.addAll(occurrenceList);//???
        }

        return matches;
    }

    private List<WordOnPage> sortWordsOnPage() {

        List<WordOnPage> queryWordsInListOfWordsOnPage = findQueryWordsInListOfWordsOnPage();

        return queryWordsInListOfWordsOnPage
                .stream()
                .sorted(Comparator.comparing(WordOnPage::getPosition).reversed())
                .collect(Collectors.toList());
    }

    private String boldenWords() {
        String closingTag = "</b>";
        String openingTag = "<b>";

        String text = clearContentFromExtraElements();
        List<WordOnPage> wordOnPageList = sortWordsOnPage();
        StringBuilder builder = new StringBuilder(text);

        for (WordOnPage w : wordOnPageList) {
            int wordLength = w.getWord().length();
            builder.insert(w.getPosition() + wordLength, closingTag);
            builder.insert(w.getPosition(), openingTag);
        }

        return builder.toString();
    }

    private String removeExtraTags() {
        StringBuilder boldenedText = new StringBuilder(boldenWords());

        Matcher matcher = REMOVE_EXTRA_TAGS_PATTERN.matcher(boldenedText);

        String s = null;
        while (matcher.find()) {
            s = matcher.replaceAll(" ");
        }

        return s == null ? boldenedText.toString() : s;
    }

    private String getShortSnippet() {
        String formattedText = removeExtraTags();

        Matcher matcher = GET_SHORT_SNIPPET_PATTERN.matcher(formattedText);

        List<String> res = new ArrayList<>();
        while (matcher.find()) {
            res.add(matcher.group());
        }
        return res.get(0);

    }

}
