package org.igor.klimov.app.lemmatizer;

public enum Language {
    RUSSIAN("ru"),
    ENGLISH("en"),
    RUSSIAN_LOCALE("ru-RU"),
    ENGLISH_LOCALE("en-US");

    private final String language;

    Language(String language) {
        this.language = language;
    }

    public String getLanguage() {
        return language;
    }
}
