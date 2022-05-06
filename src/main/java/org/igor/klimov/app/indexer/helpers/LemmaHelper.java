package org.igor.klimov.app.indexer.helpers;

import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.model.Field;
import org.igor.klimov.app.model.Lemma;
import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LemmaHelper {
    private final Map<String, Integer> lemmas; //map to calculate and hold lemmas frequencies
    private final LemmaCounter counter;
    private final Map<String, Integer> lemma2ID;
    private final LemmaRepository lemmaRepository;
    private final List<Field> fields;
    private final int siteId;

    private static final org.apache.log4j.Logger LOGGER = Logger.getLogger(LemmaHelper.class);

    public LemmaHelper(int siteId, LemmaRepository lemmaRepository, List<Field> fields) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.siteId = siteId;
        this.fields = fields;

        this.lemma2ID = new TreeMap<>();
        this.lemmas = new TreeMap<>();
        this.counter = new LemmaCounter();
    }

    private Map<String, Integer> countStringsInPageBlock(String html, String selector) {
            String text = Jsoup.parse(html).select(selector).text();
             return counter.countLemmas(text);
    }

    private Map<String, Float> getSelector2weight(){
        Map<String, Float> selector2weight = new HashMap<>();
        for(Field f : fields){
            selector2weight.put(f.getSelector(), f.getWeight());
        }
        return selector2weight;
    }

    public void addLemmasCache(Map<String, Float> word2weight) {
        Set<String> lemmasFromPage = word2weight.keySet();
        for (String s : lemmasFromPage) {
            lemmas.compute(s, (k , v) -> (v == null) ? 1 : v + 1);
        }
    }

    private Map<String, Float> getWeight4LemmasInOneBlock(String selector, String html, float weight){
        Map<String, Float> res = new HashMap<>();
        Map<String, Integer>wordsFromPageBlock = countStringsInPageBlock(html, selector);

        for(Map.Entry<String, Integer> e : wordsFromPageBlock.entrySet()){
            res.put(e.getKey(), e.getValue() * weight);
        }
        return res;
    }

    public Map<String, Float> calculateWeightForAllLemmasOnPage(String html){
        Map<String, Float>selector2weight = getSelector2weight();
        Map<String, Float> res = new HashMap();
        for(Map.Entry<String, Float> e : selector2weight.entrySet()){
            Map<String, Float> weight4LemmasInOneBlock =
                    getWeight4LemmasInOneBlock(e.getKey(), html, e.getValue());
            for(Map.Entry<String, Float> entry : weight4LemmasInOneBlock.entrySet()){
                res.compute(entry.getKey(), (k, v) -> v == null ?
                        entry.getValue() : v + entry.getValue());
            }
        }
        return res;
    }

    private Set<Lemma> getLemmas(Map<String, Integer> map) {
        return map.entrySet()
                .stream()
                .map(e -> new Lemma( e.getKey(), e.getValue(), this.siteId))
                .collect(Collectors.toSet());
    }

    public Map<String, Integer> getLemma2ID() {
        return lemma2ID;
    }

    public void writeLemmasToDb() {
        Set<Lemma> lemmasSet = getLemmas(lemmas);
        List<Lemma>saved = lemmaRepository.saveAll(lemmasSet);
        lemmas.clear();
        for(Lemma l : saved){
            lemma2ID.put(l.getLemma(), l.getId());
        }
        LOGGER.info("Lemmas saved " + saved.size());
        lemmasSet.clear();
    }
}
