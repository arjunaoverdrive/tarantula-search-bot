package org.arjunaoverdrive.app.services;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.FieldRepository;
import org.arjunaoverdrive.app.DAO.LemmaRepository;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.model.*;
import org.arjunaoverdrive.app.pagevisitor.WebPageVisitorStarter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class SiteService {

    protected final FieldRepository fieldRepository;
    protected final SiteRepository siteRepository;
    protected final LemmaRepository lemmaRepository;
    protected final PageRepository pageRepository;
    protected final JdbcTemplate jdbcTemplate;
    protected final ConfigProperties props;
    protected final AppState appState;

    private final Logger LOGGER = Logger.getLogger(SiteService.class);

    @Autowired
    public SiteService(FieldRepository fieldRepository,
                       SiteRepository siteRepository,
                       LemmaRepository lemmaRepository,
                       PageRepository pageRepository,
                       JdbcTemplate jdbcTemplate,
                       ConfigProperties props,
                       AppState appState) {
        this.fieldRepository = fieldRepository;
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.appState = appState;
    }

    protected WebPageVisitorStarter getWebPageVisitorThread(Site site2save) {
        List<Field> fields = fieldRepository.findAll();
        return new WebPageVisitorStarter(
                site2save,
                fields,
                lemmaRepository,
                pageRepository,
                siteRepository,
                jdbcTemplate,
                props,
                appState);
    }

    protected Site removeSiteDataFromDb(Site site) {
        synchronized (appState) {
            Site saved = null;
            if (!appState.isIndexing()) {
                appState.setIndexing(true);
                saved = saveSiteWhenStartIndexing(site);

                clearSiteDataFromDb(site);
                appState.setIndexing(false);
            }
            return saved;
        }
    }

    private void clearSiteDataFromDb(Site site) {
        String siteName = site.getName();
        int id = site.getId();
        removeSiteLemmas(id);
        LOGGER.info("Cleared lemmas for site " + siteName);

        List<Integer> pageIds = removeSitePages(id);
        LOGGER.info("Cleared pages for site " + siteName);

        if (pageIds.size() != 0) {
            removeSiteIndices(pageIds);
            LOGGER.info("Cleared indices for site " + siteName);
        }
    }

    private Site saveSiteWhenStartIndexing(Site site) {
        String siteName = site.getName();
        Site saved;
        site.setStatus(StatusEnum.INDEXING);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError("");
        saved = siteRepository.save(site);
        LOGGER.info("Starting indexing site " + siteName + ". Starting clearing site information");
        return saved;
    }

    private void removeSiteLemmas(int siteId) {
        List<Lemma> lemmas2delete = lemmaRepository.findBySiteId(siteId);
        lemmaRepository.deleteAll(lemmas2delete);
    }

    private List<Integer> removeSitePages(int siteId) {
        List<Page> pages2delete = pageRepository.findBySiteId(siteId);
        List<Integer> pageIds = pages2delete.stream().map(Page::getId).collect(Collectors.toList());
        pageRepository.deleteAll(pages2delete);
        return pageIds;
    }

    private void removeSiteIndices(List<Integer> pageIds) {
        StringBuilder builder = getStringWithPagesIds(pageIds);
        try {
            jdbcTemplate.execute("DELETE from index WHERE page_id IN (" + builder + ")");
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    private StringBuilder getStringWithPagesIds(List<Integer> pageIds) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pageIds.size(); i++) {
            builder.append(pageIds.get(i));
            if (i < pageIds.size() - 1) {
                builder.append(", ");
            }
        }
        return builder;
    }

}
