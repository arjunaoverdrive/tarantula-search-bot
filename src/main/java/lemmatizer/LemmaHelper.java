package lemmatizer;

import model.Lemma;
import model.Page;
import org.apache.log4j.Logger;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import pagevisitor.WebPageVisitor;
import util.DbSessionSetup;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class LemmaHelper {
    private final Map<String, Integer> lemmas;
    private final LemmaCounter counter;

    private static final org.apache.log4j.Logger LOGGER = Logger.getLogger(LemmaHelper.class);

    public LemmaHelper() throws IOException {
        this.lemmas = new HashMap<>();
        this.counter = new LemmaCounter(new RussianLuceneMorphology());
    }

    public void addLemmasToStorage(Page page) throws IOException {
        String text = page.getContent();
        Set<String> lemmasFromPage = counter.countLemmas(text).keySet();
        for(String s : lemmasFromPage){
            if(lemmas.containsKey(s)){
               lemmas.computeIfPresent(s, (key, value) -> value + 1);
            }
            else lemmas.put(s, 1);
        }
    }

    private Set<Lemma>getLemmas(Map<String, Integer>map){
        return map.entrySet().stream().map(e -> new Lemma(e.getKey(), e.getValue())).collect(Collectors.toSet());
    }


    //TODO написать метод, который получает структуру со списками лемм из title и body

    public void writeLemmasToDb() {
        Set<Lemma>lemmasSet = getLemmas(lemmas);
        lemmas.clear();
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        lemmasSet.forEach(session::save);
        LOGGER.info("saved " + lemmasSet.size());
        lemmasSet.clear();
        transaction.commit();
        session.close();
    }
}
