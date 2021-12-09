package pagevisitor;

import org.apache.log4j.Logger;
import pagevisitor.helpers.IndexHelper;
import pagevisitor.helpers.LemmaHelper;
import pagevisitor.helpers.URLsStorage;


import java.io.IOException;
import java.util.concurrent.ForkJoinPool;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class);
    public static final String DOMAIN = "https://www.svetlovka.ru/";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        try {
            Node root = new Node(DOMAIN);
            URLsStorage storage = new URLsStorage(DOMAIN);
            IndexHelper indexHelper = new IndexHelper();
            LemmaHelper lemmaHelper = new LemmaHelper();
            WebPageVisitor visitor = new WebPageVisitor(root, storage, lemmaHelper, indexHelper);
            visitor.saveRootPage();
            ForkJoinPool fjp = new ForkJoinPool();
            fjp.invoke(visitor);
            visitor.flushBufferToDb();
            lemmaHelper.writeLemmasToDb();
            visitor.saveIndiciesToDb();
            LOGGER.info("Took " + (System.currentTimeMillis() - start));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
