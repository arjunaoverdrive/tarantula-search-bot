package org.arjunaoverdrive.app.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "`index`", indexes = @javax.persistence.Index(columnList = "page_id, lemma_id"))
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name="page_id", columnDefinition = "INT NOT NULL")
    private int pageId;
    @Column(name = "lemma_id", columnDefinition = "INT NOT NULL")
    private int lemmaId;
    @Column(name = "`rank`", columnDefinition = "DOUBLE PRECISION NOT NULL")
    private float rank;

    public Index() {
    }

    public Index(int lemma_id, int pageId,  float rank) {
        this.lemmaId = lemma_id;
        this.pageId = pageId;
        this.rank = rank;
    }

    public int getId() {
        return id;
    }

    public int getPageId() {
        return pageId;
    }

    public void setPageId(int pageId) {
        this.pageId = pageId;
    }

    public int getLemmaId() {
        return lemmaId;
    }

    public void setLemmaId(int lemmaId) {
        this.lemmaId = lemmaId;
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
        if (!(o instanceof Index)) return false;
        Index index = (Index) o;
        return getPageId() == index.getPageId() && getLemmaId() == index.getLemmaId() && Float.compare(index.getRank(), getRank()) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPageId(), getLemmaId(), getRank());
    }
}
