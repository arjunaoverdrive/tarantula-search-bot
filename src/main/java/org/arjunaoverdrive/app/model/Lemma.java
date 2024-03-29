package org.arjunaoverdrive.app.model;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "lemma", indexes = @javax.persistence.Index(columnList = "site_id, lemma"))
//@SQLInsert(sql = "INSERT INTO lemma(frequency, lemma, site_id) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE frequency = frequency + VALUES(frequency)")
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name = "lemma", columnDefinition = "VARCHAR(255) NOT NULL")
    private String lemma;
    @Column(name = "frequency", columnDefinition = "INT NOT NULL")
    private int frequency;
    @Column(name = "site_id", columnDefinition = "INT NOT NULL")
    private int siteId;

    public Lemma() {
    }

    public Lemma(String lemma, int frequency, int siteId) {
        this.lemma = lemma;
        this.frequency = frequency;
        this.siteId = siteId;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String lemma) {
        this.lemma = lemma;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getSiteId() {
        return siteId;
    }

    public void setSite(int siteId) {
        this.siteId = siteId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return siteId == lemma1.siteId && lemma.equals(lemma1.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lemma, siteId);
    }

    @Override
    public String toString() {
        return "Lemma{" +
                "id=" + id +
                ", lemma='" + lemma + '\'' +
                ", frequency=" + frequency +
                '}';
    }
}
