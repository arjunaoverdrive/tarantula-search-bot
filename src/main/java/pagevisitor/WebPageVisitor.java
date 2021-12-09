package pagevisitor;

import model.Page;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import pagevisitor.helpers.IndexHelper;
import pagevisitor.helpers.IndexPrototype;
import pagevisitor.helpers.LemmaHelper;
import pagevisitor.helpers.URLsStorage;
import util.DbSessionSetup;

import java.io.IOException;
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
        Set<Node> childrenNodes = node.getChildrenNodes(connection, storage);
        if (childrenNodes == null || childrenNodes.size() == 0) {
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
        Connection connection = getConnection(node.getPath());
        Page page = createPageObject(connection);
        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);
            if(page.getCode() == 200){
                lemmaHelper.addLemmasToStorage(lemmaHelper.convertPageBlocks2stringMaps(connection));
            }
        }
        if (storage.getBuffer().size() >= BUFFER_SIZE) {
            doWrite();
        }
    }

    private void createIndexPrototypesForPage(Page p, int pageId){
        if (p.getCode() == 200) {
            Connection connection = getConnection(Main.DOMAIN + p.getPath().substring(1));
            List<Map<String, Integer>> maps =
                    lemmaHelper.convertPageBlocks2stringMaps(connection);
            Set<String> lemmas =
                    lemmaHelper.getStringsFromPageBlocks(maps);
//            Set<String>lemmas = lemmasFromPageMap.keySet();
                for(String s : lemmas){
                    float rank = lemmaHelper.getWeightForLemma(s, maps);
                    IndexPrototype ip= new IndexPrototype(pageId, s, rank);
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
        for(Page p : pageSet){
            int pageId = (int) session.save(p);
            createIndexPrototypesForPage(p, pageId);
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

    private Page createPageObject(Connection connection) {
        Page page;
        Connection.Response response = null;
        try {
            response = connection.execute();
            int statusCode = response.statusCode();
            page = new Page(response.url().getPath(), statusCode, response.body());
        } catch (HttpStatusException hse) {
            String path = hse.getUrl().substring(Main.DOMAIN.length() - 1);
            page = new Page(path, hse.getStatusCode(), "");
            LOGGER.warn(Thread.currentThread().getId() + " Page with empty content " + path);
            return page;
        } catch (IOException e) {
            page = new Page(connection.request().url().getPath(), 500, "");
            LOGGER.warn(e);
            return page;
        }
        return page;
    }

    void saveRootPage() {
        Connection connection = getConnection(Main.DOMAIN);
        Page rootPage = createPageObject(connection);
        storage.addPageURL(Main.DOMAIN);
        storage.addPage2Buffer(rootPage);
    }
    void saveIndiciesToDb(){
        indexHelper.convertPrototypes2Indices(lemmaHelper.getLemma2ID());
    }
}
