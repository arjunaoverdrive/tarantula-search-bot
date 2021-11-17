package pagevisitor;

import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;


import java.io.IOException;
import java.util.*;

import java.util.stream.Collectors;

public class Node {
    private static final Logger LOGGER = Logger.getLogger(Node.class);
    private static final String USER_AGENT = "TarantulaSearchBot (Windows; U; WindowsNT 5.1; en-US; rvl.8.1.6)" +
            "Gecko/20070725 Firefox/2.0.0.6";
    private final String path;
    private final Connection connection;
    private final List<Node> children;

    private final static Set<Node> duplicates = new HashSet<>();

    public Node(String path) {
        this.path = path;
        this.children = new ArrayList<>();
        this.connection = getConnection();
    }

    boolean addChild(Node node) {
        if (duplicates.add(node)) {
            children.add(node);
            return true;
        } else return false;
    }

    public Connection getConnection() {
        return Jsoup.connect(this.path).userAgent(USER_AGENT).referrer("http://www.google.com");
    }

    String getPath() {
        return path;
    }

    List<Node> getChildrenNodes() {
        List<Node> childrenNodes = new ArrayList<>();
        Document doc = null;
        try {
            doc = connection.get();
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
        List<String> links = doc.select("a[href]").stream()
                .map(e -> e.attr("abs:href"))
                .distinct()
                .filter(s -> s.matches(Main.DOMAIN + "(/)?.+"))
                .filter(s -> !s.contains("#"))
                .filter(s -> !s.matches(".+\\.\\w+"))
                .filter(s -> !s.matches(".+login\\?.*"))
                .collect(Collectors.toList());
        for (String childLink : links) {
            Node node = new Node(childLink);
            childrenNodes.add(node);
        }
        return childrenNodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return getPath().equals(node.getPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPath());
    }
}
