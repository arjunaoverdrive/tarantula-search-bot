package model;

import org.hibernate.annotations.SQLInsert;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "lemma")
//@SQLInsert(sql = "INSERT INTO lemma(frequency, lemma) VALUES (?, ?) ON DUPLICATE KEY UPDATE frequency = frequency + VALUES(frequency)")
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(nullable = false)
    private String lemma;
    @Column(nullable = false)
    private int frequency;

    public Lemma() {
    }

    public Lemma(String lemma, int frequency) {
        this.lemma = lemma;
        this.frequency = frequency;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lemma)) return false;
        Lemma lemma1 = (Lemma) o;
        return getId() == lemma1.getId() && getLemma().equals(lemma1.getLemma());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getLemma());
    }
}
