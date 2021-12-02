package pagevisitor;

import lemmatizer.LemmaHelper;

import model.Page;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveAction;

public class WebPageVisitor extends RecursiveAction {
    private final Node node;
    private final URLsStorage storage;
    private final LemmaHelper lemmaStorage;

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);

    private static final String USER_AGENT = "TarantulaSearchBot (Windows; U; WindowsNT 5.1; en-US; rvl.8.1.6)" +
            "Gecko/20070725 Firefox/2.0.0.6";
    private static final int BUFFER_SIZE = 100;

    public WebPageVisitor(Node node, URLsStorage storage, LemmaHelper lemmaStorage) {
        this.node = node;
        this.storage = storage;
        this.lemmaStorage = lemmaStorage;
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
            WebPageVisitor action = new WebPageVisitor(child, storage, lemmaStorage);
            action.fork();
            subActions.add(action);
        }
        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
        }
    }

    private void saveChildPageToStorage(Node node){
        Page page = createPageObject(getConnection(node.getPath()));
        if (storage.addPageURL(page.getPath())) {
            storage.addPage2Buffer(page);
            saveLemmasFromPage(page);
        }
        if (storage.getBuffer().size() >= BUFFER_SIZE) {
            storage.doWrite();
        }
    }

    private void saveLemmasFromPage(Page page){
        if(page.getCode() == 200) {
            try {
                lemmaStorage.addLemmasToStorage(page);
            } catch (IOException e) {
                LOGGER.warn(e);
            }
        }
    }

    private Connection getConnection(String path) {
        return Jsoup.connect(path).userAgent(USER_AGENT).referrer("http://www.google.com");
    }

     Page createPageObject(Connection connection) {
        Page page;
        Connection.Response response = null;
        try {
            response = connection.execute();
            int statusCode = response.statusCode();
            page = new Page(response.url().getPath(), statusCode, response.body());
        } catch (HttpStatusException hse) {
            String path = hse.getUrl().substring(Main.DOMAIN.length() - 1);
            page = new Page(path, hse.getStatusCode(), null);
            LOGGER.warn(Thread.currentThread().getId() + " Page with empty content " + path);
            return page;
        } catch (IOException e) {
            page = new Page(connection.request().url().getPath(), 400, null);
            LOGGER.warn(e);
            return page;
        }
        return page;
    }

    void saveRootPage() throws IOException {
        Connection connection = getConnection(Main.DOMAIN);
        Page rootPage = createPageObject(connection);
        storage.addPageURL(Main.DOMAIN);
        storage.addPage2Buffer(rootPage);
        storage.doWrite();
        lemmaStorage.addLemmasToStorage(rootPage);

//        try {
//            List<Element> titleElements = getElements(connection, "title");
//            List<String> textFromTitleElements = getTextFromElements(titleElements);
//            List<Element> bodyElements = getElements(connection, "body");
//            List<String> textFromBodyElements = getTextFromElements(bodyElements);
//            textFromTitleElements.forEach(System.out::println);
//            System.out.println(titleElements.size() + " elements in title");
//            textFromBodyElements.forEach(System.out::println);
//
//            System.out.println(bodyElements.size() + " elements in body");
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }



}
