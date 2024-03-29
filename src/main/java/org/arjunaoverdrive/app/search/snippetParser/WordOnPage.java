package org.arjunaoverdrive.app.search.snippetParser;

public class WordOnPage implements Comparable<WordOnPage>{

    private int position;
    private String lemma;
    private String word;

    public WordOnPage() {
    }

    public WordOnPage(int position, String lemma, String word) {
        this.position = position;
        this.lemma = lemma;
        this.word = word;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getLemma() {
        return lemma;
    }

    public void setLemma(String lemma) {
        this.lemma = lemma;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    @Override
    public String toString() {
        return "WordOnPage{" +
                "position=" + position +
                ", lemma='" + lemma + '\'' +
                ", word='" + word + '\'' +
                '}';
    }


    @Override
    public int compareTo(WordOnPage wop) {
        return this.position - wop.position;
    }
}
