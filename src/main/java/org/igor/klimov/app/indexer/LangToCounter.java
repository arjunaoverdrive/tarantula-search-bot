package org.igor.klimov.app.indexer;

import org.apache.log4j.Logger;
import org.igor.klimov.app.lemmatizer.EnglishLemmaCounter;
import org.igor.klimov.app.lemmatizer.Language;
import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.igor.klimov.app.lemmatizer.RussianLemmaCounter;

import java.io.IOException;
import java.util.HashMap;

public class LangToCounter {
    private LangToCounter langToCounter;
    private static final HashMap<String, LemmaCounter> LANGUAGE_TO_COUNTER  = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(LangToCounter.class);

    public LangToCounter() {
        populateLangToCounter();
    }

    private void populateLangToCounter(){
        try {
            LANGUAGE_TO_COUNTER.put(Language.RUSSIAN.getLanguage(), new RussianLemmaCounter());
            LANGUAGE_TO_COUNTER.put(Language.ENGLISH.getLanguage(), new EnglishLemmaCounter());
            LANGUAGE_TO_COUNTER.put(Language.RUSSIAN_LOCALE.getLanguage(), new RussianLemmaCounter());
            LANGUAGE_TO_COUNTER.put(Language.ENGLISH_LOCALE.getLanguage(), new EnglishLemmaCounter());
        } catch (IOException ioe) {
            LOGGER.error(ioe);
        }
    }

    public LemmaCounter getLemmaCounter(String lang){
        LemmaCounter counter = LANGUAGE_TO_COUNTER.get(lang);
        if(counter == null){
            throw new RuntimeException("Cannot create a LemmaCounter object for this language: " + lang);
        }
        return counter;
    }
}
