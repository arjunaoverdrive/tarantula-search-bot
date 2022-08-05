package org.arjunaoverdrive.app.services;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.FieldRepository;
import org.arjunaoverdrive.app.DAO.LemmaRepository;
import org.arjunaoverdrive.app.DAO.PageRepository;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.indexer.helpers.IndexHelper;
import org.arjunaoverdrive.app.indexer.helpers.LemmaHelper;
import org.arjunaoverdrive.app.indexer.helpers.URLsStorage;
import org.arjunaoverdrive.app.lemmatizer.LangToCounter;
import org.arjunaoverdrive.app.lemmatizer.LemmaCounter;
import org.arjunaoverdrive.app.model.*;
import org.arjunaoverdrive.app.webapp.DTO.ResultDto;
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
public class IndexOnePageService {

    private static final Logger LOGGER = Logger.getLogger(IndexOnePageService.class);
    protected final FieldRepository fieldRepository;
    protected final SiteRepository siteRepository;
    protected final LemmaRepository lemmaRepository;
    protected final PageRepository pageRepository;
    protected final JdbcTemplate jdbcTemplate;
    protected final ConfigProperties props;
    protected final AppState appState;

    @Autowired
    public IndexOnePageService(FieldRepository fieldRepository,
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

        Map<String, Float> lemmasFromPageContent = getLemmaToWeightFromPage(page, siteId);
        List<Lemma> savedLemmas = persistLemmas(savedPage, lemmasFromDb, lemmasFromPageContent);
        persistIndices(savedLemmas, lemmasFromPageContent, pageId);
    }

    private Map<String, Float> getLemmaToWeightFromPage(Page page, int siteId) throws IOException {
        List<Field> fields = fieldRepository.findAll();
        LemmaCounter counter = getLemmaCounter(page);
        LemmaHelper lemmaHelper = new LemmaHelper(siteId, lemmaRepository, fields, counter);
        Map<String, Float> lemmasFromPageContent = lemmaHelper.calculateWeightForAllLemmasOnPage(page.getContent());
        return lemmasFromPageContent;
    }

    private LemmaCounter getLemmaCounter(Page page) {
        String lang = Jsoup.parse(page.getContent()).getElementsByAttribute("lang").get(0).attributes().get("lang");
        LangToCounter langToCounter = LangToCounter.getInstance();
        return langToCounter.getLemmaCounter(lang);
    }

    private List<Lemma> persistLemmas(Page page, List<Lemma> lemmasFromDb, Map<String, Float> lemmasFromPageContent) {
        if (page.getCode() != 200) {
            return new ArrayList<>();
        }
        Set<String> strings = lemmasFromPageContent.keySet();
        List<Lemma> lemmasFromPage = getLemmasFromPage(page, strings);
        List<Lemma> lemmas2save = compareLemmasFromDbVsLemmasFromPage(lemmasFromDb, lemmasFromPage);

        List<Lemma> savedLemmas = lemmaRepository.saveAll(lemmas2save);
        LOGGER.info("Updated " + savedLemmas.size() + " lemmas");
        return savedLemmas;
    }

    private List<Lemma> getLemmasFromPage(Page page, Set<String> strings) {
        int siteId = page.getSiteId();
        List<Lemma> lemmasFromPage = new ArrayList<>();
        strings.forEach(s -> lemmasFromPage.add(new Lemma(s, 1, siteId)));
        return lemmasFromPage;
    }

    private List<Lemma> compareLemmasFromDbVsLemmasFromPage(List<Lemma> lemmasFromDb, List<Lemma> lemmasFromPage) {
        List<Lemma> lemmas2save = updateLemmasFrequencies(lemmasFromDb, lemmasFromPage);
        if (lemmasFromPage.size() != lemmasFromDb.size()) {
            lemmasFromPage.removeAll(lemmasFromDb);
            lemmas2save.addAll(lemmasFromPage);
        }
        return lemmas2save;
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

    private void persistIndices(List<Lemma> savedLemmas, Map<String, Float> lemmasFromPageContent, int pageId) {
        List<Index> indices = new ArrayList<>();
        IndexHelper indexHelper = new IndexHelper(jdbcTemplate);

        for (Lemma l : savedLemmas) {
            String lemma = l.getLemma();
            float rank = lemmasFromPageContent.get(lemma);
            indices.add(new Index(l.getId(), pageId, rank));
        }
        indexHelper.saveIndices(indices);
    }

    private List<Lemma> clearDataForPageInCaseOfDuplication(Page page) {
        int pageId = page.getId();
        String sql = "SELECT * from index WHERE page_id = " + pageId;
        List<Index> indices = executeQueryToGetIndicesList(sql, pageId);

        jdbcTemplate.execute("DELETE from index WHERE page_id =" + pageId);
        LOGGER.info("Deleted " + indices.size() + " indices for page " + page.getPath());

        List<Lemma> allLemmasById = updateLemmasFrequencies(indices);
        LOGGER.info("Retrieved " + allLemmasById.size() + " lemmas for page " + page.getPath());

        pageRepository.delete(page);
        LOGGER.info("Deleted page " + page.getPath());
        return allLemmasById;
    }

    private List<Index> executeQueryToGetIndicesList(String sql, int pageId) {
        List<Index> indices =
                jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
                    Index index = new Index();
                    index.setPageId(pageId);
                    index.setLemmaId(rs.getInt("lemma_id"));
                    index.setRank(rs.getFloat("rank"));
                    return index;
                });
        return indices;
    }

    private List<Lemma> updateLemmasFrequencies(List<Index> indices) {
        List<Integer> lemmasIds = indices.stream().map(Index::getLemmaId).collect(Collectors.toList());
        indices.clear();
        List<Lemma> allLemmasById = lemmaRepository.findAllById(lemmasIds);
        allLemmasById.forEach(l -> {
            int currentFrequency = l.getFrequency();
            l.setFrequency(--currentFrequency);
        });
        return allLemmasById;
    }
}
