package pagevisitor.helpers;

import model.Page;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class URLsStorage {
    private final String root;
    private final Set<String> children; //container for absolute paths
    private final Set<String> savedPagedPaths;
    private final Set<Page> buffer;

    public URLsStorage(String root) {
        this.root = root;
        this.buffer = new HashSet<>();
        this.children = new HashSet<>();
        this.savedPagedPaths = new HashSet<>();
    }

    public boolean addPath(String path) {
        return children.add(path);
    }

    public boolean addPageURL(String path) {
        return savedPagedPaths.add(path);
    }

    public void addPage2Buffer(Page page) {
        buffer.add(page);
    }

    public Set<Page> getBuffer() {
        return buffer;
    }

    public void clearBuffer() {
        buffer.clear();
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
