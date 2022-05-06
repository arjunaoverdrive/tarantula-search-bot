package org.igor.klimov.app.config;

import org.springframework.stereotype.Component;

@Component
public class AppState {
    private volatile boolean isIndexing;
    private volatile boolean isStopped;

    public AppState() {
        this.isIndexing = false;
    }

    public boolean isIndexing() {
        return isIndexing;
    }

    public void setIndexing(boolean indexing) {
        isIndexing = indexing;
    }

    public boolean isStopped() {
        return isStopped;
    }

    public void setStopped(boolean stopped) {
        isStopped = stopped;
    }
}
