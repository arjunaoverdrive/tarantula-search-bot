package org.arjunaoverdrive.app.config;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.lemmatizer.LangToCounter;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartup implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger LOGGER = Logger.getLogger(ApplicationStartup.class);
    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        LangToCounter langToCounter = LangToCounter.getInstance();
        LOGGER.info("init " + langToCounter.getClass());
    }
}
