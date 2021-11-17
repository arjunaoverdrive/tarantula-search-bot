package pagevisitor;

import java.util.concurrent.ForkJoinPool;

public class Main {
    public static final String DOMAIN = "https://ipfran.ru/";

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        try {
            Node root = new Node(DOMAIN);
            WebPageVisitor visitor = new WebPageVisitor(root);
            visitor.saveRootPage();
            ForkJoinPool fjp = new ForkJoinPool();
            fjp.invoke(visitor);
            WebPageVisitor.flushBufferToDb();
            System.out.println("Took " + (System.currentTimeMillis() - start));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
