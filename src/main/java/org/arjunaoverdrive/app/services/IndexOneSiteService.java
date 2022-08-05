package org.arjunaoverdrive.app.services;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.FieldRepository;
import org.arjunaoverdrive.app.DAO.LemmaRepository;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.pagevisitor.WebPageVisitorStarter;
import org.arjunaoverdrive.app.webapp.DTO.ResultDto;
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

    private Site getSiteByUrl(String siteUrl) {
        Site site = siteRepository.findByUrl(siteUrl);
        return site;
    }
}
