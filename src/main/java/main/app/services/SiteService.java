package main.app.services;

import main.app.DAO.IndexRepository;
import main.app.DAO.LemmaRepository;
import main.app.DAO.PageRepository;
import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.indexer.helpers.LemmaHelper;
import main.app.indexer.helpers.URLsStorage;
import main.app.model.*;
import main.app.pagevisitor.WebPageVisitorStarter;
import main.app.webapp.DTO.ResultDto;
import org.apache.log4j.Logger;
import org.jsoup.Connection;
import org.jsoup.UnsupportedMimeTypeException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SiteService {

    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final ConfigProperties props;
    private final AppState appState;
    private Set<WebPageVisitorStarter> starters = new HashSet<>();

    private static final Logger LOGGER = Logger.getLogger(SiteService.class);

    @Autowired
    public SiteService(SiteRepository siteRepository,
                       LemmaRepository lemmaRepository,
                       PageRepository pageRepository,
                       IndexRepository indexRepository,
                       ConfigProperties props,
                       AppState appState) {
        this.siteRepository = siteRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.props = props;
        this.appState = appState;
    }

    private Set<ConfigProperties.Site> readSitesFromConfig() {
        return props.getSites();
    }

    public void saveSites() {

        LOGGER.info("Reading sites from config");
        for (ConfigProperties.Site s : readSitesFromConfig()) {
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private WebPageVisitorStarter getWebPageVisitorThread(Site site2save) {
        return new WebPageVisitorStarter(site2save, lemmaRepository, pageRepository, siteRepository,
                props, indexRepository, appState);
    }

    private Site removeSiteDataFromDb(Site site) {
        String siteName = site.getName();
        appState.setIndexing(true);
        site.setStatus(StatusEnum.INDEXING);
        Site saved = siteRepository.save(site);
        LOGGER.info("Starting indexing site " + siteName + ". Starting clearing site information");

        int id = site.getId();
        List<Lemma> lemmas2delete = lemmaRepository.findBySiteId(id);
        lemmaRepository.deleteAll(lemmas2delete);
        LOGGER.info("Cleared lemmas for site " + siteName);

        List<Page> pages2delete = pageRepository.findBySiteId(id);
        List<Integer> pageIds = pages2delete.stream().map(Page::getId).collect(Collectors.toList());
        pageRepository.deleteAll(pages2delete);
        LOGGER.info("Cleared pages for site " + siteName);

        List<Index> indices2delete = indexRepository.findByPageIdIn(pageIds);
        indexRepository.deleteAll(indices2delete);
        LOGGER.info("Cleared indices for site " + siteName);
        appState.setIndexing(false);
        return saved;
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
        } try {
            appState.setStopped(true);
            return new ResultDto.Success();
        } catch (Exception e){
            LOGGER.error(e.getLocalizedMessage());
            return new ResultDto.Error(e.getLocalizedMessage());
        }
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
                LOGGER.error(e);
                return new ResultDto.Error(e.getLocalizedMessage());
            }
            return new ResultDto.Success();
        }
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
        URLsStorage storage = new URLsStorage(site.getUrl(), pageRepository, siteRepository, props);
        Connection connection = storage.getConnection(pageUrl);

        try {
            Page page = storage.createPageObject(connection, siteId);
            if (page.getCode() != 200) {
                throw new RuntimeException("Страница недоступна");
            }
            persistPageData(page, siteId);
        } catch (UnsupportedMimeTypeException e) {
            LOGGER.info(e);
        } catch (Exception e) {
            LOGGER.warn(e);
            throw new RuntimeException(e.getLocalizedMessage());
        } finally {
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
        if (page.getCode() != 200) {
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
        int pageId = page.getId();
        List<Map<String, Integer>> lemmasFromTitleNBody = lemmaHelper.convertTitleNBody2stringMaps(page.getContent());
        List<Index> indices = new ArrayList<>();

        for (Lemma l : savedLemmas) {
            float rank = lemmaHelper.getWeightForLemma(l.getLemma(), lemmasFromTitleNBody);
            Index i = new Index(l.getId(), pageId, rank);
            indices.add(i);
        }
        indexRepository.saveAll(indices);
        LOGGER.info("Saved " + indices.size() + " indices");
    }


    private List<Lemma> clearDataForPageInCaseOfDuplication(Page page) {
        int id = page.getId();
        List<Index> indices = indexRepository.findByPageId(id);
        indexRepository.deleteAll(indices);
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
