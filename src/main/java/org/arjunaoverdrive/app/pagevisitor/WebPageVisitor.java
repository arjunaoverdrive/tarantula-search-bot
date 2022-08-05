package org.arjunaoverdrive.app.pagevisitor;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.indexer.IndexPrototype;
import org.arjunaoverdrive.app.indexer.helpers.IndexHelper;
import org.arjunaoverdrive.app.indexer.helpers.LemmaHelper;
import org.arjunaoverdrive.app.indexer.helpers.URLsStorage;
import org.arjunaoverdrive.app.model.Page;
import org.arjunaoverdrive.app.model.Site;
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
            if (appState.isStopped()) {
                clearSubActionsList(subActions);
                break;
            }
            addChildSubAction(subActions, child);
        }
    }

    private void addChildSubAction(List<WebPageVisitor> subActions, Node child) {
        saveChildPageToStorage(child);
        WebPageVisitor action = createChildVisitor(child);
        action.fork();
        subActions.add(action);
    }

    private void clearSubActionsList(List<WebPageVisitor> subActions) {
        subActions.forEach(ForkJoinTask::tryUnfork);
        subActions.clear();
    }

    private WebPageVisitor createChildVisitor(Node child){
        return new WebPageVisitor(siteId, child, siteRepository, storage, lemmaHelper,
                indexHelper, appState);
    }

    private void saveChildPageToStorage(Node node) {
        Page page;
        Connection connection = storage.getConnection(node.getPath());
        int bufferMaxSize = storage.getBufferMaxSize();

        try {
            page = storage.createPageObject(connection, siteId);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }

        populateStorageBuffer(page);
        if (storage.getBuffer().size() >= bufferMaxSize) {
            updateSiteWithBufferData();
        }
    }

    private void populateStorageBuffer(Page page) {
        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);
            if (page.getCode() == 200 ) {
                addPageLemmasToLemmasCache(page);
            }
        }
    }

    private void addPageLemmasToLemmasCache(Page page) {
        Map<String, Float> lemma2weight =
                lemmaHelper.calculateWeightForAllLemmasOnPage(page.getContent());
        lemmaHelper.addLemmasCache(lemma2weight);
    }

    private void updateSiteWithBufferData() {
        flushBufferToDb();
        Site site = siteRepository.findById(siteId).get();
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
    }

    void flushBufferToDb() {
        List<Page> savedPages = storage.savePages();
        for (Page p : savedPages) {
            createIndexPrototypesForPage(p);
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

    void saveIndicesToDb() {
        Map<String, Integer> lemma2ID = lemmaHelper.getLemma2ID();
        try {
            indexHelper.convertPrototypes2Indices(lemma2ID);
        } catch (SQLException e) {
            LOGGER.error(e);
        }
    }

    void saveLemmas() {
        lemmaHelper.writeLemmasToDb();
    }
}
