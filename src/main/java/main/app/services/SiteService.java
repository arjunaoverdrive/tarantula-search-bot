package main.app.services;

import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.indexer.helpers.IndexHelper;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.*;
import main.app.pagevisitor.WebPageVisitorStarter;
import main.app.webapp.DTO.ResultDto;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ConfigProperties props;
    private final AppState appState;

    private static final Logger LOGGER = Logger.getLogger(SiteService.class);

    @Autowired
    public SiteService(SiteRepository siteRepository,
                       LemmaRepository lemmaRepository,
                       PageRepository pageRepository,
                       JdbcTemplate jdbcTemplate,
                       ConfigProperties props,
                       AppState appState) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
        this.appState = appState;
    }

    public void saveSites() {
        Set<ConfigProperties.Site>sitesFromConfig = readSitesFromConfig();
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
            return new ResultDto.Error("Индексация уже запущена");
        }
        try {
            appState.setStopped(false);
            saveSites();
            return new ResultDto.Success();
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
            return new ResultDto.Error(e.getLocalizedMessage());
        }
    }

    public ResultDto stopIndexing() {
        if (!appState.isIndexing() || appState.isStopped()) {
            return new ResultDto.Error("Индексация не запущена");
        }
        appState.setStopped(true);
        return new ResultDto.Success();

    }

    public ResultDto indexPage(String url) {
        Site site = getSiteContainingUrl(url);
        if (site == null) {
            appState.setIndexing(false);
            return new ResultDto.Error("Данная страница находится за пределами сайтов, " +
                    "указанных в конфигурационном файле");
        }

        synchronized (appState) {
            try {
                while (appState.isIndexing()) {
                    appState.wait();
                }
                if (!appState.isIndexing()) {
                    persistPage(site, url);
                    appState.notify();
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
                return new ResultDto.Error(e.getLocalizedMessage());
            }
            return new ResultDto.Success();
        }
    }

    private void compareSitesInDbAndConfig(Set<ConfigProperties.Site> siteFromConfig){
        List<Site> sitesFromDb = siteRepository.findAll();
        List<String> siteFromConfigNames = siteFromConfig.stream().map(ConfigProperties.Site::getName)
                .collect(Collectors.toList());
        for(Site s : sitesFromDb){
            if(!siteFromConfigNames.contains(s.getName())){
                removeSiteDataFromDb(s);
                removeSite(s);
            }
        }
    }

    private Set<ConfigProperties.Site> readSitesFromConfig() {
        return props.getSites();
    }


    private WebPageVisitorStarter getWebPageVisitorThread(Site site2save) {
        return new WebPageVisitorStarter(site2save,
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
            if(!appState.isIndexing()) {
                String siteName = site.getName();
                appState.setIndexing(true);
                site.setStatus(StatusEnum.INDEXING);
                site.setStatusTime(LocalDateTime.now());
                saved = siteRepository.save(site);
                LOGGER.info("Starting indexing site " + siteName + ". Starting clearing site information");

                int id = site.getId();
                removeSiteLemmas(id);
                LOGGER.info("Cleared lemmas for site " + siteName);

                List<Integer> pageIds = removeSitePages(id);
                LOGGER.info("Cleared pages for site " + siteName);

                removeSiteIndices(pageIds);
                LOGGER.info("Cleared indices for site " + siteName);

                appState.setIndexing(false);
            }
        return saved;
        }
    }

    private void removeSiteLemmas(int siteId){
        List<Lemma> lemmas2delete = lemmaRepository.findBySiteId(siteId);
        lemmaRepository.deleteAll(lemmas2delete);
    }

    private List<Integer> removeSitePages(int siteId){
        List<Page> pages2delete = pageRepository.findBySiteId(siteId);
        List<Integer> pageIds = pages2delete.stream().map(Page::getId).collect(Collectors.toList());
        pageRepository.deleteAll(pages2delete);
        return pageIds;
    }

    private void removeSiteIndices(List<Integer> pageIds){
        StringBuilder builder = new StringBuilder();
        for(int i = 0; i < pageIds.size(); i++){
            builder.append(pageIds.get(i));
            if(i < pageIds.size() - 1){
                builder.append(", ");
            }
        }
        try {
            jdbcTemplate.execute("DELETE from `index` WHERE page_id IN (" + builder.toString() + ")");
        }catch (Exception e){
            LOGGER.error(e.getLocalizedMessage());
        }
    }

    private void removeSite(Site s){
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

    private void persistPage(Site site, String pageUrl) {
        appState.setIndexing(true);

        int siteId = site.getId();
        URLsStorage storage = new URLsStorage(site.getUrl(), pageRepository,  props);
        Connection connection = storage.getConnection(pageUrl);

        try {
            Page page = storage.createPageObject(connection, siteId, siteRepository);
            if (page.getCode() != 200) {
                throw new RuntimeException("Страница недоступна");
            }
            persistPageData(page, siteId);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e.getLocalizedMessage());
        } catch (UnsupportedOperationException e){
            LOGGER.warn(e);
            throw new RuntimeException("Контент страницы недоступен");
        } catch (Exception e) {
            LOGGER.warn(e);
            throw new RuntimeException(e.getLocalizedMessage());
        } finally {
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
            appState.setIndexing(false);
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

        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository);
        List<Lemma> savedLemmas = persistLemmas(savedPage, lemmaHelper, lemmasFromDb);
        persistIndices(savedLemmas, lemmaHelper, savedPage);
    }

    private List<Lemma> persistLemmas(Page page, LemmaHelper lemmaHelper, List<Lemma> lemmasFromDb) {
        if (page.getCode() != 200 ) {
            return new ArrayList<>();
        }
        List<Map<String, Integer>> lemmasFromTitleNBody = lemmaHelper.convertTitleNBody2stringMaps(page.getContent());

        Set<String> strings = new HashSet<>();
        for (Map<String, Integer> map : lemmasFromTitleNBody) {
            strings.addAll(map.keySet());
        }

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
        return fromDb;
    }


    private void persistIndices(List<Lemma> savedLemmas, LemmaHelper lemmaHelper, Page page) {
        if(page.getCode() != 200) throw new RuntimeException("Page with empty content " + page.getPath());
        int pageId = page.getId();
        List<Map<String, Integer>> lemmasFromTitleNBody = lemmaHelper.convertTitleNBody2stringMaps(page.getContent());
        List<Index> indices = new ArrayList<>();
        IndexHelper indexHelper = new IndexHelper(jdbcTemplate);

        for (Lemma l : savedLemmas) {
            float rank = lemmaHelper.getWeightForLemma(l.getLemma(), lemmasFromTitleNBody);
            Index i = new Index(l.getId(), pageId, rank);
            indices.add(i);
        }
        indexHelper.doWrite(indices);
    }


    private List<Lemma> clearDataForPageInCaseOfDuplication(Page page) {
        int id = page.getId();
        String sql = "SELECT * from `index` WHERE page_id = " + id;

        List<Index> indices =
                jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
                    Index index = new Index();
                    index.setPageId(id);
                    index.setLemmaId(rs.getInt("lemma_id"));
                    index.setRank(rs.getFloat("rank"));
                    return index;
                });
        jdbcTemplate.execute("DELETE from `index` WHERE page_id =" + id);
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

}
