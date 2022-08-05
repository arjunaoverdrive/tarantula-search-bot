package org.arjunaoverdrive.app.search.snippetParser;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.lemmatizer.LemmaCounter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SnippetCreator implements SnippetParser {
    private final List<String> queryLemmas;
    private final String html;
    private final LemmaCounter counter;

    private static final Pattern WORD_ON_PAGE_PATTERN = Pattern.compile("\\b[А-яA-z]+\\b");
    private static final Pattern REMOVE_EXTRA_TAGS_PATTERN = Pattern.compile("</b>[\\s\\p{Punct}]?<b>");
    private static final Pattern GET_SHORT_SNIPPET_PATTERN = Pattern.compile("(\\s.{0,25}<b>.{0,150}</b>).{0,25}\\s");
    private static final String OPENING_TAG = "<b>";
    private static final String CLOSING_TAG = "</b>";

    private static final Logger LOGGER = Logger.getLogger(SnippetCreator.class);

    public SnippetCreator(List<String> queryLemmas, String html, LemmaCounter counter) {
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
            String word = matcher.group().toLowerCase();
            String lemma = counter.getBasicForm(word);
            if (lemma == null) {
                continue;
            }

            if(queryLemmas.contains(lemma)) {
                wordsFromPage.add(new WordOnPage(matcher.start(), lemma, matcher.group()));
            }
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


    private List<WordOnPage> sortWordsOnPage() {
        List<WordOnPage> wordsOnPage = new ArrayList<>();
        getLemmaToWordOnPageMap().values().forEach(wordsOnPage::addAll);

        return wordsOnPage
                .stream()
                .sorted(Comparator.comparing(WordOnPage::getPosition).reversed())
                .collect(Collectors.toList());
    }

    private String boldenWords() {
        String text = clearContentFromExtraElements();
        List<WordOnPage> wordOnPageList = sortWordsOnPage();
        return insertBoldTags(text, wordOnPageList);

    }

    private String insertBoldTags(String text, List<WordOnPage> wordOnPageList) {
        StringBuilder builder = new StringBuilder(text);
        for (WordOnPage w : wordOnPageList) {
            int wordLength = w.getWord().length();
            builder.insert(w.getPosition() + wordLength, CLOSING_TAG);
            builder.insert(w.getPosition(), OPENING_TAG);
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

        return res.stream()
                .sorted(Comparator.comparing(String::length).reversed())
                        .collect(Collectors.toList()).get(0);

    }

}
