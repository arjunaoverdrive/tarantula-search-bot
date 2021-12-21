package pagevisitor;

import model.Page;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import pagevisitor.helpers.IndexHelper;
import pagevisitor.helpers.IndexPrototype;
import pagevisitor.helpers.LemmaHelper;
import pagevisitor.helpers.URLsStorage;
import util.DbSessionSetup;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.RecursiveAction;

public class WebPageVisitor extends RecursiveAction {
    private final Node node;
    private final URLsStorage storage;
    private final LemmaHelper lemmaHelper;
    private final IndexHelper indexHelper;

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);

    private static final String USER_AGENT = "TarantulaSearchBot (Windows; U; WindowsNT 5.1; en-US; rvl.8.1.6)" +
            "Gecko/20070725 Firefox/2.0.0.6";
    private static final int BUFFER_SIZE = 100;
    private static int count = 0;

    public WebPageVisitor(Node node, URLsStorage storage, LemmaHelper lemmaHelper, IndexHelper indexHelper) {
        this.node = node;
        this.storage = storage;
        this.lemmaHelper = lemmaHelper;
        this.indexHelper = indexHelper;
    }

    @Override
    protected void compute() {
        Connection connection = getConnection(node.getPath());
        Set<Node> childrenNodes;
        try {
            childrenNodes = node.getChildrenNodes(connection, storage);
        } catch (UnsupportedMimeTypeException e) {
            return;
        }
        if (childrenNodes.size() == 0){
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
            WebPageVisitor action = new WebPageVisitor(child, storage, lemmaHelper, indexHelper);
            action.fork();
            subActions.add(action);
        }
        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
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
                lemmaHelper.addLemmasToStorage(lemmaHelper.convertPageBlocks2stringMaps(page.getContent()));
            }
        }
        if (storage.getBuffer().size() >= BUFFER_SIZE) {
            doWrite();
        }
    }

    private void createIndexPrototypesForPage(Page p) {
        if (p.getCode() == 200) {
            int pageId = p.getId();
            String html = p.getContent();
            List<Map<String, Integer>> maps =
                    lemmaHelper.convertPageBlocks2stringMaps(html);
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

    private void doWrite() {
        Set<Page> pageSet = new HashSet<>(storage.getBuffer());
        storage.clearBuffer();
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        for (Page p : pageSet) {
            int pageId = (int) session.save(p);
            p.setId(pageId);
            createIndexPrototypesForPage(p);
        }
        transaction.commit();
        count += pageSet.size();
        LOGGER.info("Successfully saved " + pageSet.size() + " pages; count " + count);
        pageSet.clear();
        session.close();
    }

    void flushBufferToDb() {
        doWrite();
    }

    private Connection getConnection(String path) {
        return Jsoup.connect(path).userAgent(USER_AGENT).referrer("http://www.google.com");
    }

    private Page createPageObject(Connection connection) throws UnsupportedMimeTypeException {
        Page page;
        Connection.Response response;
        try {
            response = connection.execute();
            int statusCode = response.statusCode();
            String path = URLDecoder.decode(response.url().getPath(), StandardCharsets.UTF_8);
            page = new Page(path, statusCode, response.body());
        } catch (HttpStatusException hse) {
            String path = URLDecoder.decode(hse.getUrl().substring(Main.DOMAIN.length() - 1), StandardCharsets.UTF_8);
            page = new Page(path, hse.getStatusCode(), "");
            LOGGER.warn(Thread.currentThread().getId() + " Page with empty content " + path);
            return page;
        } catch (UnsupportedMimeTypeException e) {
            throw new UnsupportedMimeTypeException("unsupported type", e.getMimeType(), e.getUrl());
        } catch (IOException e) {
            String path = URLDecoder.decode(connection.request().url().getPath(), StandardCharsets.UTF_8);
            page = new Page(path, 500, "");
            LOGGER.warn(e);
            return page;
        }
        return page;
    }

    void saveRootPage() {
        Connection connection = getConnection(Main.DOMAIN);
        Page rootPage = null;
        try {
            rootPage = createPageObject(connection);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e);
        }
        storage.addPageURL(Main.DOMAIN);
        storage.addPage2Buffer(rootPage);
        lemmaHelper.addLemmasToStorage(lemmaHelper.convertPageBlocks2stringMaps(rootPage.getContent()));
    }

    void saveIndicesToDb() {
        indexHelper.convertPrototypes2Indices(lemmaHelper.getLemma2ID());
    }
}
