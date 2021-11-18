package pagevisitor;

import java.util.*;

public class Node {
    private final String path;
    private final static Set<String> UNIQUE_PATHS = new HashSet<>();

    public Node(String path) {
        this.path = path;
    }

    boolean isUnique() {
        return UNIQUE_PATHS.add(this.getPath());
    }

    String getPath() {
        return path;
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
