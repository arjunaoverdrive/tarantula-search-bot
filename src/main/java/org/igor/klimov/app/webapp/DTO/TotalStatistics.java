package org.igor.klimov.app.webapp.DTO;

public class TotalStatistics {

    private int sites;
    private int pages;
    private int lemmas;
    private boolean isIndexing;

    public TotalStatistics() {
    }

    public TotalStatistics(int sites, int pages, int lemmas, boolean isIndexing) {
        this.sites = sites;
        this.pages = pages;
        this.lemmas = lemmas;
        this.isIndexing = isIndexing;
    }

    public int getSites() {
        return sites;
    }

    public void setSites(int sites) {
        this.sites = sites;
    }

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public int getLemmas() {
        return lemmas;
    }

    public void setLemmas(int lemmas) {
        this.lemmas = lemmas;
    }

    public boolean isIsIndexing() {
        return isIndexing;
    }

    public void setIsIndexing(boolean indexing) {
        isIndexing = indexing;
    }
}
