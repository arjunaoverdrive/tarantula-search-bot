package pagevisitor;

import java.util.concurrent.ForkJoinPool;

public class Main {
    public static final String DOMAIN = "https://volochek.life/";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        try {
            Node root = new Node(DOMAIN);
            URLsStorage storage = new URLsStorage(DOMAIN);
            WebPageVisitor visitor = new WebPageVisitor(root, storage);
            visitor.saveRootPage();
            ForkJoinPool fjp = new ForkJoinPool();
            fjp.invoke(visitor);
            visitor.flushBufferToDb();
            System.out.println("Took " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
