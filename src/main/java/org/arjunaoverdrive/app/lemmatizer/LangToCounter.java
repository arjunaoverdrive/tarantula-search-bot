package org.arjunaoverdrive.app.lemmatizer;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;

@Component
public class LangToCounter {
    private static LangToCounter langToCounter;
    private static final HashMap<String, LemmaCounter> LANGUAGE_TO_COUNTER = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(LangToCounter.class);

    private LangToCounter() {
        populateLangToCounter();
    }

    public static LangToCounter getInstance() {
        if (langToCounter == null) {
            langToCounter = new LangToCounter();
        }
        return langToCounter;
    }

    private void populateLangToCounter() {

        RussianLemmaCounter russianLemmaCounter = null;
        EnglishLemmaCounter englishLemmaCounter = null;
        try {
            russianLemmaCounter = new RussianLemmaCounter();
            englishLemmaCounter = new EnglishLemmaCounter();
        } catch (IOException e) {
            LOGGER.error(e);
        }
        LANGUAGE_TO_COUNTER.put(Language.RUSSIAN.getLanguage(), russianLemmaCounter);
        LANGUAGE_TO_COUNTER.put(Language.ENGLISH.getLanguage(), englishLemmaCounter);
        LANGUAGE_TO_COUNTER.put(Language.RUSSIAN_LOCALE.getLanguage(), russianLemmaCounter);
        LANGUAGE_TO_COUNTER.put(Language.ENGLISH_LOCALE.getLanguage(), englishLemmaCounter);
    }

    public LemmaCounter getLemmaCounter(String lang) {
        LemmaCounter counter = LANGUAGE_TO_COUNTER.get(lang);
        if (counter == null) {
            throw new RuntimeException("Cannot create a LemmaCounter object for this language: " + lang);
        }
        return counter;
    }
}
