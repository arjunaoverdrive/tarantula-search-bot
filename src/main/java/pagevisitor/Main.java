package pagevisitor;

import model.Lemma;
import org.apache.log4j.Logger;
import search.FoundPage;
import search.SearchHelper;

import java.io.IOException;
import java.util.List;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class);
    public static final String DOMAIN = "https://www.svetlovka.ru/";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        try {
//            Node root = new Node(DOMAIN);
//            URLsStorage storage = new URLsStorage(DOMAIN);
//            IndexHelper indexHelper = new IndexHelper();
//            LemmaHelper lemmaHelper = new LemmaHelper();
//            WebPageVisitor visitor = new WebPageVisitor(root, storage, lemmaHelper, indexHelper);
//            visitor.saveRootPage();
//            ForkJoinPool fjp = new ForkJoinPool();
//            fjp.invoke(visitor);
//            visitor.flushBufferToDb();
//            lemmaHelper.writeLemmasToDb();
//            visitor.saveIndicesToDb();
            String query = "С помощью этой техники у детей совершенствуется мелкая моторика, происходит развитие глазомера.";
            SearchHelper sh = new SearchHelper(query);
            List<Lemma> res = sh.getSortedList();
            res.forEach(System.out::println);
            List<FoundPage> pages = sh.sortFoundPages();
            pages.forEach(System.out::println);
            LOGGER.info("Took " + (System.currentTimeMillis() - start));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
