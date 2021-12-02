package pagevisitor;

import lemmatizer.LemmaHelper;


import java.util.concurrent.ForkJoinPool;

public class Main {
    public static final String DOMAIN = "https://www.svetlovka.ru/";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        try {
            Node root = new Node(DOMAIN);
            URLsStorage storage = new URLsStorage(DOMAIN);
            LemmaHelper lemmaStorage = new LemmaHelper();
            WebPageVisitor visitor = new WebPageVisitor(root, storage, lemmaStorage);
            visitor.saveRootPage();
            ForkJoinPool fjp = new ForkJoinPool();
            fjp.invoke(visitor);
            lemmaStorage.writeLemmasToDb();
            storage.flushBufferToDb();
            System.out.println("Took " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
