package org.igor.klimov.app.services;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.FieldRepository;
import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.DAO.PageRepository;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.config.ConfigProperties;
import org.igor.klimov.app.model.*;
import org.igor.klimov.app.pagevisitor.WebPageVisitorStarter;
import org.igor.klimov.app.webapp.DTO.ResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SiteService {

    protected final FieldRepository fieldRepository;
    protected final SiteRepository siteRepository;
    protected final LemmaRepository lemmaRepository;
    protected final PageRepository pageRepository;
    protected final JdbcTemplate jdbcTemplate;
    protected final ConfigProperties props;
    protected final AppState appState;

    private static final Logger LOGGER = Logger.getLogger(SiteService.class);

    @Autowired
    public SiteService(FieldRepository fieldRepository, SiteRepository siteRepository,
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

    public void saveSites() {
        Set<ConfigProperties.Site> sitesFromConfig = readSitesFromConfig();
        compareSitesInDbAndConfig(sitesFromConfig);

        LOGGER.info("Reading sites from config");
        for (ConfigProperties.Site s : sitesFromConfig) {
            if (appState.isStopped()) {                   //not to drop data for all sites if indexing is interrupted
                break;
            }
            String siteName = s.getName();
            Site site = getSiteByName(siteName);

            if (site == null) {
                site = siteRepository.save(new Site(LocalDateTime.now(), "",
                        s.getUrl(), s.getName(), StatusEnum.INDEXING));
            } else {
                site = removeSiteDataFromDb(site);
            }

            WebPageVisitorStarter recursiveActionStarter = getWebPageVisitorThread(site);
            Thread t = new Thread(recursiveActionStarter);
            t.start();
            try {
                t.join();
            } catch (Exception e) {
                LOGGER.error(e);
            }
        }
    }

    public ResultDto startReindexing() {
        if (appState.isIndexing()) {
            return new ResultDto.Error("Indexing is already in progress");
        }
        appState.setStopped(false);
        saveSites();
        return new ResultDto.Success();
    }

    public ResultDto stopIndexing() {
        if (!appState.isIndexing() || appState.isStopped()) {
            return new ResultDto.Error("Indexing is not in progress");
        }
        appState.setStopped(true);
        return new ResultDto.Success();
    }

    private Site getSiteByName(String siteName) {
        Site site = siteRepository.findByName(siteName);
        return site;
    }

    protected Site getSiteByUrl(String siteUrl) {
        Site site = siteRepository.findByUrl(siteUrl);
        return site;
    }

    private void compareSitesInDbAndConfig(Set<ConfigProperties.Site> siteFromConfig) {
        List<Site> sitesFromDb = siteRepository.findAll();
        List<String> siteFromConfigNames = siteFromConfig.stream().map(ConfigProperties.Site::getName)
                .collect(Collectors.toList());
        for (Site s : sitesFromDb) {
            if (!siteFromConfigNames.contains(s.getName())) {
                removeSiteDataFromDb(s);
                removeSite(s);
            }
        }
    }

    private Set<ConfigProperties.Site> readSitesFromConfig() {
        return props.getSites();
    }

    protected WebPageVisitorStarter getWebPageVisitorThread(Site site2save) {
        List<Field> fields = getFields();
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

    protected List<Field> getFields() {
        return fieldRepository.findAll();
    }

    protected Site removeSiteDataFromDb(Site site) {
        synchronized (appState) {
            Site saved = null;
            if (!appState.isIndexing()) {
                String siteName = site.getName();
                appState.setIndexing(true);
                site.setStatus(StatusEnum.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                site.setLastError("");
                saved = siteRepository.save(site);
                LOGGER.info("Starting indexing site " + siteName + ". Starting clearing site information");

                int id = site.getId();
                removeSiteLemmas(id);
                LOGGER.info("Cleared lemmas for site " + siteName);

                List<Integer> pageIds = removeSitePages(id);
                LOGGER.info("Cleared pages for site " + siteName);

                if (pageIds.size() != 0) {
                    removeSiteIndices(pageIds);
                    LOGGER.info("Cleared indices for site " + siteName);
                }
                appState.setIndexing(false);
            }
            return saved;
        }
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
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pageIds.size(); i++) {
            builder.append(pageIds.get(i));
            if (i < pageIds.size() - 1) {
                builder.append(", ");
            }
        }
        try {
            jdbcTemplate.execute("DELETE from index WHERE page_id IN (" + builder + ")");
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    private void removeSite(Site s) {
        siteRepository.delete(s);
    }
}
