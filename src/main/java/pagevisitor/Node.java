package pagevisitor;

import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import indexer.helpers.URLsStorage;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;


public class Node {
    private final String path;

    private static final Logger LOGGER = Logger.getLogger(Node.class);

    public Node(String path) {
        this.path = path;
    }

    String getPath() {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    Set<Node> getChildrenNodes(Connection connection, URLsStorage storage) throws UnsupportedMimeTypeException {
        Set<Node> childrenNodes = new HashSet<>();
        Document doc = null;
        try {
            doc = connection.get();
        } catch (UnsupportedMimeTypeException e){
            throw new UnsupportedMimeTypeException(e.getMessage(), e.getMimeType(), e.getUrl());
        }
        catch (IOException e) {
            LOGGER.warn(e);
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
                .filter(s -> !s.contains("login"))
                .filter(s -> !s.endsWith("PDF") && !s.endsWith("doc") && !s.endsWith("jpg"))
                .filter(s -> !s.matches(".+http(s)?.+"))
                .collect(Collectors.toList());
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
