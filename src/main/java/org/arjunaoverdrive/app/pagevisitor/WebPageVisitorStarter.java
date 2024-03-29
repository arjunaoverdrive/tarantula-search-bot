package org.arjunaoverdrive.app.pagevisitor;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.LemmaRepository;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.indexer.helpers.IndexHelper;
import org.arjunaoverdrive.app.indexer.helpers.LemmaHelper;
import org.arjunaoverdrive.app.indexer.helpers.URLsStorage;
import org.arjunaoverdrive.app.lemmatizer.LangToCounter;
import org.arjunaoverdrive.app.lemmatizer.LemmaCounter;
import org.arjunaoverdrive.app.model.Field;
import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.model.StatusEnum;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class WebPageVisitorStarter implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitorStarter.class);

    private final List<Field> fields;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Site site;
    private final ConfigProperties props;
    private final AppState appState;

    public WebPageVisitorStarter(Site site,
                                 List<Field> fields,
                                 LemmaRepository lemmaRepository,
                                 PageRepository pageRepository,
                                 SiteRepository siteRepository,
                                 JdbcTemplate jdbcTemplate,
                                 ConfigProperties props,
                                 AppState appState) {
        this.site = site;
        this.fields = fields;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.appState = appState;

    }

    @Override
    public void run() {
        long start = System.currentTimeMillis();
        synchronized (appState) {
            while (appState.isIndexing()) {
                try {
                    appState.wait();
                } catch (InterruptedException e) {
                    LOGGER.error(e);
                }
            }
            try {
                if (!appState.isStopped()) {
                    invokeWebPageVisitorTask();
                }
                if (appState.isStopped()) {
                    throw new RuntimeException("Indexing was stopped");
                }
            } catch (Exception e) {
                saveSite(site, StatusEnum.FAILED, e.getMessage());
                LOGGER.error(e);
            } finally {
                appState.setIndexing(false);
                appState.notify();
                LOGGER.info("Thread " + Thread.currentThread().getId() + " Took " + (System.currentTimeMillis() - start) + " ms");
            }
        }
    }

    private void invokeWebPageVisitorTask() throws IOException {
        appState.setIndexing(true);
        LOGGER.info(Thread.currentThread().getName() + " Starting indexing site: " + site.getName());
        WebPageVisitor visitor = initWebPageVisitor(site);
        saveVisitorData(visitor);
        saveSite(site, StatusEnum.INDEXED, "");
    }

    private WebPageVisitor initWebPageVisitor(Site site) throws IOException {
        int siteId = site.getId();
        String root = site.getUrl();

        Node rootNode = new Node(root, root);
        URLsStorage storage = new URLsStorage(root, pageRepository, props);
        IndexHelper indexHelper = new IndexHelper(jdbcTemplate);
        LemmaCounter counter = getLemmaCounter(root);
        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository, fields, counter);
        WebPageVisitor visitor  = new WebPageVisitor(
                siteId,
                rootNode,
                siteRepository,
                storage,
                lemmaHelper,
                indexHelper,
                appState);
        return visitor;
    }

    private LemmaCounter getLemmaCounter(String root)  {
        String lang = getRootPageLanguage(root);
        LangToCounter langToCounter = LangToCounter.getInstance();
        LemmaCounter counter = langToCounter.getLemmaCounter(lang);
        if (counter == null){
            throw new RuntimeException("Cannot create lemmaCounter object for this language: " + lang);
        }
        return counter;
    }

    private String getRootPageLanguage(String root) {
        String lang = null;
        try {
            lang = Jsoup.connect(root)
                    .execute()
                    .parse()
                    .getElementsByAttribute("lang")
                    .get(0)
                    .attributes().get("lang");
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return lang;
    }


    private void saveVisitorData(WebPageVisitor visitor) throws ConcurrentModificationException {
        ForkJoinPool fjp = new ForkJoinPool();
        fjp.invoke(visitor);
        visitor.flushBufferToDb();
        visitor.saveLemmas();
        visitor.saveIndicesToDb();
    }

    private void saveSite(Site site, StatusEnum status, String lastError) {
        site.setStatusTime(LocalDateTime.now());
        site.setStatus(status);
        site.setLastError(lastError);
        siteRepository.save(site);
    }
}
