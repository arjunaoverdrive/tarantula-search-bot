package main.app.pagevisitor;

import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.indexer.IndexPrototype;
import main.app.indexer.helpers.IndexHelper;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.Page;
import main.app.model.Site;
import main.app.config.ConfigProperties;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveAction;

public class WebPageVisitor extends RecursiveAction {
    private final int siteId;
    private final Node node;
    private final SiteRepository siteRepository;
    private final URLsStorage storage;
    private final LemmaHelper lemmaHelper;
    private final IndexHelper indexHelper;
    private final ConfigProperties props;
    private final AppState appState;


    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);

    private static final int BUFFER_SIZE = 100;

    public WebPageVisitor(int siteId, Node node, SiteRepository siteRepository, URLsStorage storage, LemmaHelper lemmaHelper, IndexHelper indexHelper, ConfigProperties props, AppState appState) {
        this.siteId = siteId;
        this.node = node;
        this.siteRepository = siteRepository;
        this.storage = storage;
        this.lemmaHelper = lemmaHelper;
        this.indexHelper = indexHelper;
        this.props = props;
        this.appState = appState;
    }

    @Override
    protected void compute() {
        if (!appState.isStopped()) {
            Connection connection = getConnection(node.getPath());
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
                WebPageVisitor action = new WebPageVisitor(siteId, child, siteRepository, storage, lemmaHelper, indexHelper, props, appState);
                action.fork();
                subActions.add(action);
            }
            for (WebPageVisitor action : subActions) {
                action.quietlyJoin();
            }
        }
    }

    private void saveChildPageToStorage(Node node) {
        Page page;
        Connection connection = getConnection(node.getPath());
        try {
            page = createPageObject(connection);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }
        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);
            if (page.getCode() == 200) {
                lemmaHelper.addLemmasToStorage( lemmaHelper.convertTitleNBody2stringMaps( page.getContent() ) );
            }
        }
        if (storage.getBuffer().size() >= BUFFER_SIZE) {
            flushBufferToDb();
        }
    }

    private void createIndexPrototypesForPage(Page p) {
        if (p.getCode() == 200) {
            int pageId = p.getId();
            String html = p.getContent();
            List<Map<String, Integer>> maps =
                    lemmaHelper.convertTitleNBody2stringMaps(html);
            Set<String> lemmas = new HashSet<>();
            try {
                lemmas = lemmaHelper.getStringsFromPageBlocks(maps);
            } catch (NullPointerException npe) {
                LOGGER.warn(p.getPath() + " " + npe);
            }
            for (String s : lemmas) {
                float rank = lemmaHelper.getWeightForLemma(s, maps);
                IndexPrototype ip = new IndexPrototype(pageId, s, rank);
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

    private Connection getConnection(String path) {
        return Jsoup.connect(path).userAgent( getUserAgent() ).referrer("http://www.google.com");
    }

    private String getUserAgent() {
        return props.getUserAgent();
    }

    private Page createPageObject(Connection connection) throws UnsupportedMimeTypeException {
        Page page;
        Connection.Response response;
        Site site = siteRepository.findById(siteId).get();
        try {
            response = connection.execute();
            int statusCode = response.statusCode();
            String path = URLDecoder.decode(response.url().getPath() , StandardCharsets.UTF_8);
            page = new Page(path, statusCode, response.body(), siteId);
        } catch (HttpStatusException hse) {
            String path = URLDecoder.decode(hse.getUrl().substring( site.getUrl().length() ) , StandardCharsets.UTF_8);
            page = new Page(path, hse.getStatusCode(), "", siteId);
            return page;
        } catch (UnsupportedMimeTypeException e) {
            throw new UnsupportedMimeTypeException("unsupported type", e.getMimeType(), e.getUrl());
        } catch (IOException e) {
            String path = URLDecoder.decode(connection.request().url().getPath() , StandardCharsets.UTF_8);
            page = new Page(path, 500, "", siteId);
            LOGGER.warn(e + " " + path);
            return page;
        }
        return page;
    }

    void saveRootPage() {
        Site site = siteRepository.findById(siteId).get();
        Connection connection = getConnection(site.getUrl());
        Page rootPage = null;
        try {
            rootPage = createPageObject(connection);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e);
        }
        storage.addPageURL(site.getUrl());
        storage.addPage2Buffer(rootPage);
        lemmaHelper.addLemmasToStorage( lemmaHelper.convertTitleNBody2stringMaps(rootPage.getContent()));
    }

    void saveIndicesToDb() {
        try {
            indexHelper.convertPrototypes2Indices(lemmaHelper.getLemma2ID());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    void saveLemmas(){
        lemmaHelper.writeLemmasToDb();
    }
}
