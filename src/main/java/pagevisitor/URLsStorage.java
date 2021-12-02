package pagevisitor;

import model.Page;
import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import util.DbSessionSetup;

import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class URLsStorage {
    private final String root;
    private final Set<String> children;
    private final Set<String> savedPagedPaths;
    private final Set<Page> buffer;

    private static final Logger LOGGER = Logger.getLogger(URLsStorage.class);
    private static int count = 0;


    public URLsStorage(String root) {
        this.root = root;
        this.buffer = new HashSet<>();
        this.children = new HashSet<>();
        this.savedPagedPaths = new HashSet<>();
    }

    public boolean addPath(String path){
        return children.add(path);
    }

    public boolean addPageURL(String path){
        return savedPagedPaths.add(path);
    }

    public void addPage2Buffer(Page page){
        buffer.add(page);
    }

    public Set<Page> getBuffer() {
        return buffer;
    }
    public void clearBuffer(){
        buffer.clear();
    }

    void doWrite() {
        Set<Page> pageSet = new HashSet<>(buffer);
        clearBuffer();
        SessionFactory sessionFactory = DbSessionSetup.getSessionSetup();
        Session session = sessionFactory.openSession();
        Transaction transaction = session.beginTransaction();
        pageSet.forEach(session::save);
        transaction.commit();
        count += pageSet.size();
        LOGGER.info("Successfully saved " + pageSet.size() + " pages; count " + count);
        pageSet.clear();
        session.close();
    }

    void flushBufferToDb() {
        doWrite();
        DbSessionSetup.getSessionSetup().close();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof URLsStorage)) return false;
        URLsStorage that = (URLsStorage) o;
        return root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return Objects.hash(root);
    }
}
