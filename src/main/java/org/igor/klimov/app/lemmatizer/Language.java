package org.igor.klimov.app.lemmatizer;

public enum Language {
    RUSSIAN("ru"),
    ENGLISH("en");

    private final String lang;

    Language(String lang) {
        this.lang = lang;
    }

    public String getLang() {
        return lang;
    }
}
