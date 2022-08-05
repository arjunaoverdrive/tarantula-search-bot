package org.arjunaoverdrive.app.search;

public class FoundPage {
    private int siteId;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;

    public FoundPage(int siteId, String uri, String title, String snippet, float relevance) {
        this.siteId = siteId;
        this.uri = uri;
        this.title = title;
        this.snippet = snippet;
        this.relevance = relevance;
    }

    public int getSiteId() {
        return siteId;
    }

    public void setSiteId(int siteId) {
        this.siteId = siteId;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public float getRelevance() {
        return relevance;
    }

    public void setRelevance(float relevance) {
        this.relevance = relevance;
    }

    @Override
    public String toString() {
        return "FoundPage{" +
                "uri='" + uri + '\'' +
                ", title='" + title + '\'' +
                ", snippet='" + snippet + '\'' +
                ", relevance=" + relevance +
                '}';
    }
}
