package org.igor.klimov.app.services;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.FieldRepository;
import org.igor.klimov.app.DAO.LemmaRepository;
import org.igor.klimov.app.DAO.PageRepository;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.config.ConfigProperties;
import org.igor.klimov.app.lemmatizer.LangToCounter;
import org.igor.klimov.app.indexer.helpers.IndexHelper;
import org.igor.klimov.app.indexer.helpers.LemmaHelper;
import org.igor.klimov.app.indexer.helpers.URLsStorage;
import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.igor.klimov.app.model.*;
import org.igor.klimov.app.pagevisitor.WebPageVisitorStarter;
import org.igor.klimov.app.webapp.DTO.ResultDto;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SiteService {

    private final FieldRepository fieldRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigProperties props;
    private final AppState appState;

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
            Site site = siteRepository.findByName(s.getName());

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

    public ResultDto indexPage(String url) throws Exception {
        synchronized (appState) {

            Site site = getSiteContainingUrl(url);
            if (url.isEmpty()) {
                appState.setIndexing(false);
                throw new IllegalArgumentException("The URL is not specified");
            }
            if (site == null) {
                appState.setIndexing(false);
                return new ResultDto.Error("This page is outside of the specified websites");
            }
            try {
                appState.setIndexing(true);
                persistPage(site, url);
                appState.setIndexing(false);
                appState.notify();
            } catch (IOException e) {
                appState.setIndexing(false);
                LOGGER.error(e.getMessage());
                throw new IOException(e.getMessage());
            }
            return new ResultDto.Success();
        }
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


    private WebPageVisitorStarter getWebPageVisitorThread(Site site2save) {
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

    private Site removeSiteDataFromDb(Site site) {
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

    private Site getSiteContainingUrl(String url) {
        List<Site> sites = siteRepository.findAll();
        Site site = null;
        for (Site s : sites) {
            if (url.startsWith(s.getUrl()))
                site = s;
        }
        return site;
    }

    private void persistPage(Site site, String pageUrl) throws IOException {

        int siteId = site.getId();
        URLsStorage storage = new URLsStorage(site.getUrl(), pageRepository, props);
        Connection connection = storage.getConnection(pageUrl);

        try {
            Page page = storage.createPageObject(connection, siteId);
            persistPageData(page, siteId);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e.getLocalizedMessage());
        } catch (UnsupportedOperationException e) {
            LOGGER.warn(e);
            throw new UnsupportedOperationException("Page content is not available");
        } catch (IOException e) {
            LOGGER.warn(e);
            throw new IOException(e.getLocalizedMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    private void persistPageData(Page page, int siteId) throws IOException {
        Page pageFromDb = pageRepository.findByPath(page.getPath());

        List<Lemma> lemmasFromDb = null;
        if (pageFromDb != null) {
            LOGGER.info("Found duplicate of the page " + page.getPath() + " Clearing page data");
            lemmasFromDb = clearDataForPageInCaseOfDuplication(pageFromDb);
        }
        Page savedPage = pageRepository.save(page);
        int pageId = savedPage.getId();


        List<Field> fields = getFields();
        LemmaCounter counter = getLemmaCounter(page);
        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository, fields, counter);
        Map<String, Float> lemmasFromPageContent = lemmaHelper.calculateWeightForAllLemmasOnPage(page.getContent());
        List<Lemma> savedLemmas = persistLemmas(savedPage, lemmasFromDb, lemmasFromPageContent);
        persistIndices(savedLemmas, lemmasFromPageContent, pageId);
    }

    private LemmaCounter getLemmaCounter(Page page){
        String lang = Jsoup.parse(page.getContent()).getElementsByAttribute("lang").get(0).attributes().get("lang");
        LangToCounter langToCounter = LangToCounter.getInstance();
        return langToCounter.getLemmaCounter(lang);
    }



    private List<Lemma> persistLemmas(Page page, List<Lemma> lemmasFromDb,
                                      Map<String, Float> lemmasFromPageContent) {
        if (page.getCode() != 200) {
            return new ArrayList<>();
        }

        Set<String> strings = lemmasFromPageContent.keySet();

        int siteId = page.getSiteId();
        List<Lemma> lemmasFromPage = new ArrayList<>();
        for (String s : strings) {
            Lemma l = new Lemma(s, 1, siteId);
            lemmasFromPage.add(l);
        }

        List<Lemma> lemmas2save = updateLemmasFrequencies(lemmasFromDb, lemmasFromPage);
        if (lemmasFromPage.size() != lemmasFromDb.size()) {
            lemmasFromPage.removeAll(lemmasFromDb);
            lemmas2save.addAll(lemmasFromPage);
        }

        List<Lemma> savedLemmas = lemmaRepository.saveAll(lemmas2save);
        LOGGER.info("Updated " + savedLemmas.size() + " lemmas");
        return savedLemmas;
    }


    private List<Lemma> updateLemmasFrequencies(List<Lemma> fromDb, List<Lemma> fromPage) {
        for (Lemma p : fromPage) {
            for (Lemma db : fromDb) {
                int frequency = p.getLemma().equals(db.getLemma()) ? (db.getFrequency() + 1) : db.getFrequency();
                db.setFrequency(frequency);
            }
        }
        return new ArrayList<>(fromDb);
    }


    private void persistIndices(List<Lemma> savedLemmas, Map<String, Float> lemmasFromPageContent,
                                int pageId) {
        List<Index> indices = new ArrayList<>();
        IndexHelper indexHelper = new IndexHelper(jdbcTemplate);

        for (Lemma l : savedLemmas) {
            String lemma = l.getLemma();
            float rank = lemmasFromPageContent.get(lemma);
            Index i = new Index(l.getId(), pageId, rank);
            indices.add(i);
        }
        indexHelper.doWrite(indices);
    }


    private List<Lemma> clearDataForPageInCaseOfDuplication(Page page) {
        int id = page.getId();
        String sql = "SELECT * from index WHERE page_id = " + id;

        List<Index> indices =
                jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
                    Index index = new Index();
                    index.setPageId(id);
                    index.setLemmaId(rs.getInt("lemma_id"));
                    index.setRank(rs.getFloat("rank"));
                    return index;
                });
        jdbcTemplate.execute("DELETE from index WHERE page_id =" + id);
        LOGGER.info("Deleted " + indices.size() + " indices for page " + page.getPath());
        List<Integer> lemmaIds = indices.stream().map(Index::getLemmaId).collect(Collectors.toList());
        indices.clear();

        List<Lemma> allLemmasById = lemmaRepository.findAllById(lemmaIds);
        allLemmasById.forEach(l -> {
            int currentFrequency = l.getFrequency();
            l.setFrequency(--currentFrequency);
        });
        LOGGER.info("Retrieved " + allLemmasById.size() + " lemmas for page " + page.getPath());

        pageRepository.delete(page);
        LOGGER.info("Deleted page " + page.getPath());
        return allLemmasById;
    }

    private List<Field> getFields() {
        return fieldRepository.findAll();
    }
}
