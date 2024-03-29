package org.arjunaoverdrive.app.pagevisitor;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.indexer.helpers.URLsStorage;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Node {
    private final String path;
    private final String root;
    private static final String EXTENSIONS =
            ".+\\.((doc)?(docx)?(pdf)?(PDF)?(xls)?(xlsx)?(pptx)?(jpg)?(jpeg)?(gif)?(png)?(JPEG)?(GIF)?(PNG)?(JPG)?){1}$";
    private static final String INVALID_URL = ".+http(s)?.+";


    private static final Logger LOGGER = Logger.getLogger(Node.class);

    public Node(String root, String path) {
        this.path = path;
        this.root = root;
    }

    String getPath() {
        return URLDecoder.decode(path, StandardCharsets.UTF_8);
    }

    Set<Node> getChildrenNodes(Connection connection, URLsStorage storage) throws UnsupportedMimeTypeException {
        Set<Node> childrenNodes = new HashSet<>();
        Document doc = getDocument(connection);
        List<String> links = getPathsListFromDocument(doc);
        populateChildNodes(storage, childrenNodes, links);
        return childrenNodes;
    }

    private Document getDocument(Connection connection) throws UnsupportedMimeTypeException {
        Document doc = null;
        try {
            doc = connection.get();
        } catch (UnsupportedMimeTypeException e) {
            throw new UnsupportedMimeTypeException(e.getMessage(), e.getMimeType(), e.getUrl());
        } catch (IOException e) {
            LOGGER.warn(e + " " + connection.request().url());
        }
        return doc;
    }

    private void populateChildNodes(URLsStorage storage, Set<Node> childrenNodes, List<String> links) {
        for (String childLink : links) {
            if (storage.addPath(childLink)) {
                Node child = new Node(root, childLink);
                childrenNodes.add(child);
            }
        }
    }

    private List<String> getPathsListFromDocument(Document doc) {
        return doc.select("a[href]").stream()
                .map(e -> e.attr("abs:href"))
                .distinct()
                .filter(s -> s.matches(root + "(/)?.+"))
                .filter(s -> !s.contains("#"))
                .filter(s -> !s.contains("?"))
                .filter(s -> !s.contains("login"))
                .filter(s -> !s.matches(EXTENSIONS))
                .filter(s -> !s.matches(INVALID_URL))
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
