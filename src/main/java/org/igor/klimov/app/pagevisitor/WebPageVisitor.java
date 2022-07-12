package org.igor.klimov.app.pagevisitor;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.indexer.IndexPrototype;
import org.igor.klimov.app.indexer.helpers.IndexHelper;
import org.igor.klimov.app.indexer.helpers.LemmaHelper;
import org.igor.klimov.app.indexer.helpers.URLsStorage;
import org.igor.klimov.app.model.Page;
import org.igor.klimov.app.model.Site;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public WebPageVisitor(int siteId, Node node, SiteRepository siteRepository, URLsStorage storage,
                          LemmaHelper lemmaHelper, IndexHelper indexHelper, AppState appState) {
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
        }
        catch (UnsupportedMimeTypeException e) {
            return;
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            LOGGER.error(e.getStackTrace());
        }

        List<WebPageVisitor> subActions = new LinkedList<>();

        computeChildrenNodes(childrenNodes, subActions);

        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
        }
    }

    private void computeChildrenNodes(Set<Node>childrenNodes, List<WebPageVisitor>subActions){
        for (Node child : childrenNodes) {
            saveChildPageToStorage(child);
            WebPageVisitor action = createChildVisitor(child);
            action.fork();
            subActions.add(action);
            if (appState.isStopped()) {
                subActions.forEach(ForkJoinTask::tryUnfork);
                subActions.clear();
                break;
            }
        }
    }

    private WebPageVisitor createChildVisitor(Node child){
        return new WebPageVisitor(siteId, child, siteRepository, storage, lemmaHelper,
                indexHelper, appState);
    }

    private void saveChildPageToStorage(Node node) {
        Page page;
        Connection connection = storage.getConnection(node.getPath());

        try {
            page = storage.createPageObject(connection, siteId);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }

        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);

            if (page.getCode() == 200 ) {
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
            Map<String, Float> word2weight = lemmaHelper.calculateWeightForAllLemmasOnPage(html);

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

    void saveIndicesToDb() {
        try {
            indexHelper.convertPrototypes2Indices(lemmaHelper.getLemma2ID());
        } catch (SQLException e) {
            LOGGER.error(e);
        }
    }

    void saveLemmas() {
        lemmaHelper.writeLemmasToDb();
    }
}
