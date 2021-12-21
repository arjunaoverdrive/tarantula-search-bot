package pagevisitor.helpers;

import lemmatizer.LemmaCounter;
import model.Lemma;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import util.DbSessionSetup;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LemmaHelper {
    private final Map<String, Integer> lemmas; //map to calculate and hold lemmas frequencies
    private final LemmaCounter counter;
    private final Map<String, Integer> lemma2ID;

    private static final float BODY_WEIGHT = 0.8f;
    private static final float TITLE_WEIGHT = 1.0f;

    private static final org.apache.log4j.Logger LOGGER = Logger.getLogger(LemmaHelper.class);

    public LemmaHelper() throws IOException {
        this.lemma2ID = new TreeMap<>();
        this.lemmas = new TreeMap<>();
        this.counter = new LemmaCounter();
    }

    private Map<String, Integer> countStringsInPageBlock(String html, String css) {

            String text = Jsoup.parse(html).select(css).text();
             return counter.countLemmas(text);
    }

    public List<Map<String, Integer>> convertPageBlocks2stringMaps(String html) {
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
            if (lemmas.containsKey(s)) {
                lemmas.computeIfPresent(s, (key, value) -> value + 1);
            } else lemmas.put(s, 1);
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
                .map(e -> new Lemma(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());
    }

    public Map<String, Integer> getLemma2ID() {
        return lemma2ID;
    }

    public void writeLemmasToDb() {
        Set<Lemma> lemmasSet = getLemmas(lemmas);
        lemmas.clear();
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        lemmasSet.forEach(l -> {
            int id = (int) session.save(l);
            lemma2ID.put(l.getLemma(), id);
        });
        LOGGER.info("Lemmas saved " + lemmasSet.size());
        lemmasSet.clear();
        transaction.commit();
        session.close();
        DbSessionSetup.getSessionSetup().close();
    }
}
