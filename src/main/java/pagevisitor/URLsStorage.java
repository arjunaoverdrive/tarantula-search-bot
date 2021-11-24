package pagevisitor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class URLsStorage {
    private final String root;
    private final Set<String> children;
    private final Set<String> savedPagedPaths;

    public URLsStorage(String root) {
        this.root = root;
        this.children = new HashSet<>();
        this.savedPagedPaths = new HashSet<>();
    }

    public boolean addPath(String path){
        return children.add(path);
    }

    public boolean addPage2save(String path){
        return savedPagedPaths.add(path);
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
