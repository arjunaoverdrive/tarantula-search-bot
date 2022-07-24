package org.igor.klimov.app.services;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.FieldRepository;
import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.DAO.PageRepository;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.config.ConfigProperties;
import org.igor.klimov.app.model.Site;
import org.igor.klimov.app.pagevisitor.WebPageVisitorStarter;
import org.igor.klimov.app.webapp.DTO.ResultDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class IndexOneSiteService extends SiteService{

    private static final Logger LOGGER = Logger.getLogger(IndexOneSiteService.class);

    public IndexOneSiteService(FieldRepository fieldRepository,
                               SiteRepository siteRepository,
                               LemmaRepository lemmaRepository,
                               PageRepository pageRepository,
                               JdbcTemplate jdbcTemplate,
                               ConfigProperties props,
                               AppState appState) {
        super(fieldRepository, siteRepository, lemmaRepository, pageRepository, jdbcTemplate, props, appState);
    }

    public ResultDto indexSite(String url) {
        if (appState.isIndexing()) {
            return new ResultDto.Error("Indexing is already in progress");
        }
        appState.setStopped(false);
        doIndexSite(url);
        return new ResultDto.Success();

    }

    public void doIndexSite(String url) {
        if (url.isEmpty()) {
            throw new IllegalArgumentException("The URL is not specified");
        }

        Site site = getSiteByUrl(url);
        site = removeSiteDataFromDb(site);

        WebPageVisitorStarter webPageVisitorThread = getWebPageVisitorThread(site);
        Thread t = new Thread(webPageVisitorThread);
        try {
            t.start();
        } catch (Exception e) {
            appState.setIndexing(false);
            appState.notify();
            LOGGER.error(e);
        }
    }
}
