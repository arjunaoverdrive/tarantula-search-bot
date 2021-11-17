package pagevisitor;

import model.Page;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import util.DbSessionSetup;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.RecursiveAction;


public class WebPageVisitor extends RecursiveAction {
    private final Node node;
    private static final Logger LOGGER = Logger.getLogger(WebPageVisitor.class);
    private static final int BUFFER_SIZE = 200;
    private final static Set<Page> pages = new HashSet<>();
    private final static Set<String> pathSet = new HashSet<>();

    public WebPageVisitor(Node node) {
        this.node = node;
    }

    @Override
    protected void compute() {
        List<Node> childNodes = node.getChildrenNodes();
        if (childNodes == null) {
            return;
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            LOGGER.error(e.getStackTrace());
        }
        List<WebPageVisitor> subActions = new ArrayList<>();
        for (Node child : childNodes) {
            if (node.addChild(child)) {
                Page page = createPageObject(child.getConnection());
                if (pathSet.add(page.getPath())) {
                    pages.add(page);
                }
                if (pages.size() >= BUFFER_SIZE) {
                    Set<Page> pageSet = new HashSet<>(pages);
                    pages.clear();
                    doWrite(pageSet);
                }
                WebPageVisitor task = new WebPageVisitor(child);
                task.fork();
                subActions.add(task);
            }
        }
        for (WebPageVisitor action : subActions) {
            action.quietlyJoin();
        }
    }

    void saveRootPage() {
        Page rootPage = createPageObject(this.node.getConnection());
        Set<Page> rootList = new HashSet<>();
        rootList.add(rootPage);
        pathSet.add(this.node.getPath());
        doWrite(rootList);
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
            LOGGER.info("Page with empty content " + path);
            return page;
        } catch (IOException e) {
            LOGGER.error(e.getStackTrace());
        }
        return page;
    }

    static void doWrite(Set<Page> pages) {
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        pages.stream().peek(System.out::println).forEach(session::save);
        transaction.commit();
        LOGGER.info("Successfully saved " + pages.size() + " pages");
    }

    static void flushBufferToDb() {
        doWrite(new HashSet<>(pages));
        DbSessionSetup.getSessionSetup().close();
    }
}
