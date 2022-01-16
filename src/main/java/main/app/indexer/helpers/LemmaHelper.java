package main.app.indexer.helpers;

import main.app.DAO.LemmaRepository;
import main.app.lemmatizer.LemmaCounter;
import main.app.model.Lemma;
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
    private final int siteId;

    private static final float BODY_WEIGHT = 0.8f;
    private static final float TITLE_WEIGHT = 1.0f;

    private static final org.apache.log4j.Logger LOGGER = Logger.getLogger(LemmaHelper.class);

    public LemmaHelper(int siteId, LemmaRepository lemmaRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.siteId = siteId;
        this.lemma2ID = new TreeMap<>();
        this.lemmas = new TreeMap<>();
        this.counter = new LemmaCounter();
    }

    private Map<String, Integer> countStringsInPageBlock(String html, String css) {
            String text = Jsoup.parse(html).select(css).text();
             return counter.countLemmas(text);
    }

    public List<Map<String, Integer>> convertTitleNBody2stringMaps(String html) {
        List<Map<String, Integer>> maps = new ArrayList<>();
        maps.add(countStringsInPageBlock(html, "title"));
        maps.add(countStringsInPageBlock(html, "body"));
        return maps;
    }

    public Set<String> getStringsFromPageBlocks(List<Map<String, Integer>> maps) {
        Set<String> stringsFromTitle =
                new HashSet<>(maps.get(0).keySet());
        Set<String> stringsFromBody =
                new HashSet<>(maps.get(1).keySet());
        stringsFromTitle.addAll(stringsFromBody);

        return stringsFromTitle;
    }

    public void addLemmasToStorage(List<Map<String, Integer>> maps) {
        Set<String> lemmasFromPage = getStringsFromPageBlocks(maps);
        for (String s : lemmasFromPage) {
            lemmas.compute(s, (k , v) -> (v == null) ? 1 : v + 1);
        }
    }

    public float getWeightForLemma(String s, List<Map<String, Integer>> maps) {
        Map<String, Integer> title = maps.get(0);
        Map<String, Integer> body = maps.get(1);
        float res = 0;
        if (title.containsKey(s)) {
            res += (title.get(s) * TITLE_WEIGHT);
        }
        if (body.containsKey(s)) {
            res += (body.get(s) * BODY_WEIGHT);
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
