package org.igor.klimov.app.search.snippetParser;

import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.igor.klimov.app.search.WordOnPage;
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

    public SnippetCreator(List<String> queryLemmas, String html, LemmaCounter counter) {

        this.queryLemmas = queryLemmas;
        this.html = html;
        this.counter = counter;
    }

    @Override
    public String create() {
        String snippet = getShortSnippet();
        return snippet;
    }

    private String clearContentFromExtraElements() {

        Document document = Jsoup.parse(html, "UTF-8");

        document.getElementsByTag("header").remove();
        document.getElementsByTag("noscript").remove();
        document.getElementsByTag("footer").remove();
        document.getElementsByTag("script").remove();

        return document.wholeText();
    }


    private List<WordOnPage> getWordsOnPageList(){

        String text = clearContentFromExtraElements();

        Pattern pattern = Pattern.compile("\\b[А-яA-z]+\\b");

        Matcher matcher = pattern.matcher(text);

        List<WordOnPage> wordsFromPage = new ArrayList<>();

        while(matcher.find()){
            WordOnPage wop = new WordOnPage();

            String lemma = counter.getBasicForm(matcher.group().toLowerCase());
            if(lemma == null) continue;
            wop.setLemma(lemma);
            wop.setPosition(matcher.start());
            wop.setWord(matcher.group());

            wordsFromPage.add(wop);
        }

        return wordsFromPage;
    }

    private Map<String, List<WordOnPage>> getLemmaToWordOnPageMap(){

        List<WordOnPage> wordsOnPage = getWordsOnPageList();

        Map<String, List<WordOnPage>> lemmaToWordOnPageMap = new HashMap<>();

        for(WordOnPage w : wordsOnPage){
            String l = w.getLemma();

            if(lemmaToWordOnPageMap.get(l) == null){

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

    private List<WordOnPage> findQueryWordsInListOfWordsOnPage(){

        List <WordOnPage> matches = new ArrayList<>();

        Map<String, List<WordOnPage>> lemmaToWordOnPage =  getLemmaToWordOnPageMap();

        for(String l : queryLemmas){
            matches.addAll(lemmaToWordOnPage.get(l));
        }

        return matches;
    }

    private List<WordOnPage> sortWordsOnPage(){

        List<WordOnPage> queryWordsInListOfWordsOnPage = findQueryWordsInListOfWordsOnPage();

        return queryWordsInListOfWordsOnPage
                .stream()
                .sorted(Comparator.comparing(WordOnPage::getPosition).reversed())
                .collect(Collectors.toList());
    }

    private String boldenWords(){
        String closingTag = "</b>";
        String openingTag = "<b>";

        String text = clearContentFromExtraElements();
        List<WordOnPage> wordOnPageList = sortWordsOnPage();
        StringBuilder builder = new StringBuilder(text);

        for(WordOnPage w : wordOnPageList){
            int wordLength = w.getWord().length();
            builder.insert(w.getPosition() + wordLength, closingTag );
            builder.insert(w.getPosition(), openingTag);
        }

        return builder.toString();
    }

    private String removeExtraTags(){
        StringBuilder boldenedText = new StringBuilder(boldenWords());

        Pattern pattern = Pattern.compile("</b>[\\s\\p{Punct}]?<b>");
        Matcher matcher = pattern.matcher(boldenedText);

        String s = null;
        while(matcher.find()){
             s = matcher.replaceAll(" ");
        }

        return s == null ? boldenedText.toString() : s;
    }

    private String getShortSnippet(){
        String formattedText = removeExtraTags();

        Pattern pattern = Pattern.compile("(.*<b>.*</b>.*)\\p{Punct}?");
        Matcher matcher = pattern.matcher(formattedText);

        List<String> res = new ArrayList<>();
        while (matcher.find()){
            res.add(matcher.group());
        }
        return res.get(0);

    }

}
