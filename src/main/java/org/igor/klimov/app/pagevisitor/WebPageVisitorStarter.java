package org.igor.klimov.app.pagevisitor;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.DAO.PageRepository;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.config.ConfigProperties;
import org.igor.klimov.app.indexer.helpers.IndexHelper;
import org.igor.klimov.app.indexer.helpers.LemmaHelper;
import org.igor.klimov.app.indexer.helpers.URLsStorage;
import org.igor.klimov.app.model.Field;
import org.igor.klimov.app.model.Site;
import org.igor.klimov.app.model.StatusEnum;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class WebPageVisitorStarter implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitorStarter.class);
    private final List<Field>fields;
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
                    appState.setIndexing(true);
                    WebPageVisitor visitor = initWebPageVisitor(site);
                    saveVisitorData(visitor);
                    saveSite(site, StatusEnum.INDEXED, "");
                }
                if (appState.isStopped()) {
                    saveSite(site, StatusEnum.FAILED, "Индексация была остановлена");
                    LOGGER.error("Indexing was interrupted");
                }
            } catch (Exception e) {
                saveSite(site, StatusEnum.FAILED, e.getMessage());
                LOGGER.error(e.toString());
            } finally {
                appState.setIndexing(false);
                appState.notify();
                LOGGER.info("Thread " + Thread.currentThread().getId() + " Took " + (System.currentTimeMillis() - start));
            }
        }
    }

    private WebPageVisitor initWebPageVisitor(Site site) {
        int siteId = site.getId();
        String root = site.getUrl();

        Node rootNode = new Node(root, root);
        URLsStorage storage = new URLsStorage(root, pageRepository, props);
        IndexHelper indexHelper = new IndexHelper(jdbcTemplate);
        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository, fields);

        return new WebPageVisitor(
                siteId,
                rootNode,
                siteRepository,
                storage,
                lemmaHelper,
                indexHelper,
                appState);
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
