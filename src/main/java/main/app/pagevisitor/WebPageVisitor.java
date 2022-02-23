package main.app.pagevisitor;

import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.indexer.IndexPrototype;
import main.app.indexer.helpers.IndexHelper;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.Page;
import main.app.model.Site;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

public class WebPageVisitor extends RecursiveAction {
    private final int siteId;
    private final Node node;
    private final SiteRepository siteRepository;
    private final URLsStorage storage;
    private final LemmaHelper lemmaHelper;
    private final IndexHelper indexHelper;
    private final AppState appState;

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);
//    private static final int BUFFER_SIZE = 100;

    public WebPageVisitor(int siteId, Node node, SiteRepository siteRepository, URLsStorage storage,
                          LemmaHelper lemmaHelper, IndexHelper indexHelper,
                          AppState appState) {
        this.siteId = siteId;
        this.node = node;
        this.siteRepository = siteRepository;
        this.storage = storage;
        this.lemmaHelper = lemmaHelper;
        this.indexHelper = indexHelper;
        this.appState = appState;
    }

    @Override
    protected void compute() {
        Connection connection = storage.getConnection(node.getPath());
        Set<Node> childrenNodes;

        try {
            childrenNodes = node.getChildrenNodes(connection, storage);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            LOGGER.error(e.getStackTrace());
        }

        List<WebPageVisitor> subActions = new LinkedList<>();
        for (Node child : childrenNodes) {
            saveChildPageToStorage(child);
            WebPageVisitor action = new WebPageVisitor(siteId, child, siteRepository, storage, lemmaHelper,
                    indexHelper, appState);
            action.fork();
            subActions.add(action);
            if (appState.isStopped()) {
                subActions.forEach(ForkJoinTask::tryUnfork);
                subActions.clear();
                break;
            }
        }
        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
        }
    }

    private void saveChildPageToStorage(Node node) {
        Page page;
        Connection connection = storage.getConnection(node.getPath());
        try {
            page = storage.createPageObject(connection, siteId, siteRepository);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }
        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);
            if (page.getCode() == 200) {
                lemmaHelper.addLemmasCache(
                        lemmaHelper.calculateWeightForAllLemmasOnPage(page.getContent()));
            }
        }
        int bufferMaxSize = storage.getBufferMaxSize();
        if (storage.getBuffer().size() >= bufferMaxSize) {
            flushBufferToDb();
            Site site = siteRepository.findById(siteId).get();
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }


    private void createIndexPrototypesForPage(Page p) {
        if (p.getCode() == 200) {
            int pageId = p.getId();
            String html = p.getContent();
            Map<String, Float>word2weight = lemmaHelper.calculateWeightForAllLemmasOnPage(html);

            for (Map.Entry<String, Float> e : word2weight.entrySet()) {
                IndexPrototype ip = new IndexPrototype(pageId, e.getKey(), e.getValue());
                indexHelper.addIndexPrototype(ip);
            }
        }
    }

    void flushBufferToDb() {
        List<Page> savedPages = storage.doWrite();
        for (Page p : savedPages) {
            createIndexPrototypesForPage(p);
        }
    }

    void saveRootPage() {
        Site site = siteRepository.findById(siteId).get();
        Connection connection = storage.getConnection(site.getUrl());
        Page rootPage = null;
        try {
            rootPage = storage.createPageObject(connection, siteId, siteRepository);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e);
        }
        storage.addPageURL(site.getUrl());
        storage.addPage2Buffer(rootPage);
        String pageContent = rootPage.getContent();
        if (pageContent == null) {
            throw new RuntimeException("Ошибка индексации: главная страница сайта недоступна");
        }
        lemmaHelper.addLemmasCache(lemmaHelper.calculateWeightForAllLemmasOnPage(pageContent));
    }

    void saveIndicesToDb() {
        try {
            indexHelper.convertPrototypes2Indices(lemmaHelper.getLemma2ID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void saveLemmas() {
        lemmaHelper.writeLemmasToDb();
    }

}
