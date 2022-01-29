package main.app.search;

public class WordOnPage {

    private int position;
    private String lemma;
    private String word;

    public WordOnPage() {
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
}
