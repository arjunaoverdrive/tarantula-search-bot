package pagevisitor;

import model.Page;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import util.DbSessionSetup;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;


public class WebPageVisitor extends RecursiveAction {
    private final Node node;
    private final URLsStorage storage;

    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);

    private static final String USER_AGENT = "TarantulaSearchBot (Windows; U; WindowsNT 5.1; en-US; rvl.8.1.6)" +
            "Gecko/20070725 Firefox/2.0.0.6";
    private static final int BUFFER_SIZE = 100;

    private final static Set<Page> PAGES = new HashSet<>();

    private static int count = 0;

    public WebPageVisitor(Node node, URLsStorage storage) {
        this.node = node;
        this.storage = storage;
    }

    @Override
    protected void compute() {
        Set<Node> childrenNodes = getChildrenNodes(node);
        if (childrenNodes == null || childrenNodes.size() == 0) {
            return;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            LOGGER.error(e.getStackTrace());
        }
        List<WebPageVisitor> subActions = new ArrayList<>();
        for (Node child : childrenNodes) {
            Page page = createPageObject(getConnection(child.getPath()));
            if (storage.addPage2save(page.getPath())) {
                PAGES.add(page);
            }
            if (PAGES.size() >= BUFFER_SIZE) {
                Set<Page> pageSet = new HashSet<>(PAGES);
                PAGES.clear();
                doWrite(pageSet);
            }
            WebPageVisitor action = new WebPageVisitor(child, storage);
            action.fork();
            subActions.add(action);
        }
        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
        }

    }

    private Connection getConnection(String path) {
        return Jsoup.connect(path).userAgent(USER_AGENT).referrer("http://www.google.com");
    }

    private Set<Node> getChildrenNodes(Node node) {
        Connection connection = getConnection(node.getPath());
        Set<Node> childrenNodes = new HashSet<>();
        Document doc = null;
        try {
            doc = connection.get();
        } catch (IOException e) {
            LOGGER.error(e);
        }
        List<String> links = getPathsListFromDocument(doc);
        for (String childLink : links) {
            if (storage.addPath(childLink)) {
                Node child = new Node(childLink);
                childrenNodes.add(child);
            }
        }
        return childrenNodes;
    }

    private List<String> getPathsListFromDocument(Document doc) {
        return doc.select("a[href]").stream()
                .map(e -> e.attr("abs:href"))
                .distinct()
                .filter(s -> s.matches(Main.DOMAIN + "(/)?.+"))
                .filter(s -> !s.contains("#"))
                .filter(s -> !s.matches(".+\\.\\w+"))
                .filter(s -> !s.matches(".+login\\?.*"))
                .collect(Collectors.toList());
    }

    private Page createPageObject(Connection connection) {
        Page page = new Page();
        Connection.Response response = null;
        try {
            response = connection.execute();
            int statusCode = response.statusCode();
            page.setCode(statusCode);
            page.setPath(response.url().getPath());
            page.setContent(response.body());
        } catch (HttpStatusException hse) {
            String path = hse.getUrl().substring(Main.DOMAIN.length() - 1);
            page.setCode(hse.getStatusCode());
            page.setPath(path);
            LOGGER.warn(Thread.currentThread().getId() + " Page with empty content " + path);
            return page;
        } catch (IOException e) {
            page.setPath(connection.request().url().getPath());
            page.setCode(400);
            LOGGER.warn(e);
            return page;
        }
        return page;
    }

    void saveRootPage() {
        Page rootPage = createPageObject(getConnection(Main.DOMAIN));
        Set<Page> rootPageSet = new HashSet<>();
        rootPageSet.add(rootPage);
        storage.addPage2save(Main.DOMAIN);
        doWrite(rootPageSet);
    }

    private static void doWrite(Set<Page> pages) {
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        pages.forEach(session::save);
        transaction.commit();
        count += pages.size();
        LOGGER.info("Successfully saved " + pages.size() + " pages; count " + count);
        session.close();
    }

    void flushBufferToDb() {
        doWrite(new HashSet<>(PAGES));
        DbSessionSetup.getSessionSetup().close();
    }
}