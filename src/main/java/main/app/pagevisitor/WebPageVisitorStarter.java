package main.app.pagevisitor;

import main.app.DAO.IndexRepository;
import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.indexer.helpers.IndexHelper;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.Site;
import main.app.model.StatusEnum;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ForkJoinPool;

public class WebPageVisitorStarter implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitorStarter.class);

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final Site site;
    private final ConfigProperties props;
    private final IndexRepository indexRepository;
    private final AppState appState;

    public WebPageVisitorStarter(Site site,
                                 LemmaRepository lemmaRepository,
                                 PageRepository pageRepository,
                                 SiteRepository siteRepository,
                                 ConfigProperties props,
                                 IndexRepository indexRepository,
                                 AppState appState) {
        this.site = site;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.props = props;
        this.indexRepository = indexRepository;
        this.appState = appState;
    }

    @Override
    public void run() {
        Site foundInDb = siteRepository.findByName(site.getName());
        int id = foundInDb == null ? siteRepository.save(site).getId() : foundInDb.getId();
        synchronized (appState) {
            long start = System.currentTimeMillis();

            try {
                while (appState.isIndexing()) {
                    appState.wait();
                }
                if (!appState.isStopped()) {
                    appState.setIndexing(true);
                    WebPageVisitor visitor = initWebPageVisitor(id);
                    saveVisitorData(visitor);
                    saveSite(site, StatusEnum.INDEXED, "");
                }
                if (appState.isStopped()) {
                    LOGGER.error("Indexing was interrupted ");
                    throw new RuntimeException("Indexing was interrupted");
                }
            } catch (Exception e) {
                saveSite(site, StatusEnum.FAILED, e.getMessage());
                LOGGER.error(e);
            } finally {
                LOGGER.info("Thread " + Thread.currentThread().getId() + " Took " + (System.currentTimeMillis() - start));
                appState.setIndexing(false);
                appState.notifyAll();
            }
        }
    }

    private WebPageVisitor initWebPageVisitor(int siteId) throws IOException {
        String root = site.getUrl();

        Node rootNode = new Node(root, root);
        URLsStorage storage = new URLsStorage(root, pageRepository);
        IndexHelper indexHelper = new IndexHelper(indexRepository);
        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository);

        return new WebPageVisitor(siteId, rootNode, siteRepository,
                storage, lemmaHelper, indexHelper, props, appState);
    }

    private void saveVisitorData(WebPageVisitor visitor) {
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

    public Thread getThread() {
        return Thread.currentThread();
    }
}
