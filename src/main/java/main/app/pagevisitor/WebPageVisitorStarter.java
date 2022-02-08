package main.app.pagevisitor;

import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.indexer.helpers.IndexHelper;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.Field;
import main.app.model.Site;
import main.app.model.StatusEnum;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
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
                saveSite(site, StatusEnum.FAILED, e.getLocalizedMessage());
                LOGGER.error(e);
            } finally {
                appState.setIndexing(false);
                appState.notify();
                LOGGER.info("Thread " + Thread.currentThread().getId() + " Took " + (System.currentTimeMillis() - start));
            }
        }
    }

    private WebPageVisitor initWebPageVisitor(Site site) throws IOException {
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
        visitor.saveRootPage();
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
