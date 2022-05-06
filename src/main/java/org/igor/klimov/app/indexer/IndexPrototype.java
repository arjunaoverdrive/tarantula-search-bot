package org.igor.klimov.app.indexer;

import java.util.Objects;

public class IndexPrototype {

    private int pageId;
    private String lemma;
    private float rank;

    public IndexPrototype(int pageId, String lemma , float rank) {
        this.lemma = lemma;
        this.rank = rank;
        this.pageId = pageId;
    }

    public int getPageId() {
        return pageId;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String lemma) {
        this.lemma = lemma;
    }

    public float getRank() {
        return rank;
    }

    public void setRank(float rank) {
        this.rank = rank;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexPrototype)) return false;
        IndexPrototype that = (IndexPrototype) o;
        return getPageId() == that.getPageId() && Float.compare(that.getRank(), getRank()) == 0 && getLemma().equals(that.getLemma());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPageId(), getLemma(), getRank());
    }

    @Override
    public String toString() {
        return "IndexPrototype{" +
                "pageId=" + pageId +
                ", lemma='" + lemma + '\'' +
                ", rank=" + rank +
                '}';
    }
}
