package model;

import javax.persistence.*;

@Entity
@Table(name = "`index`")
public class Index {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name="page_id", nullable = false)
    private int pageId;
    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;
    @Column(name = "`rank`")
    private float rank;

    public Index() {
    }

    public Index(int lemma_id, int pageId,  float rank) {
        this.pageId = pageId;
        this.lemmaId = lemma_id;
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
}
