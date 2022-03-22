package main.app.services;

import main.app.DAO.SiteRepository;
import main.app.config.AppState;
import main.app.config.ConfigProperties;
import main.app.model.Site;
import main.app.search.FoundPage;
import main.app.search.SearchCache;
import main.app.search.SearchHelper;
import main.app.webapp.DTO.SearchDto;
import main.app.webapp.DTO.SearchResultDto;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final SiteRepository siteRepository;
    private final JdbcTemplate jdbcTemplate;
    private boolean cached;
    private final SearchCache cache;
    private final AppState appState;
    private final ConfigProperties props;
    private static final Logger LOGGER = Logger.getLogger(SearchService.class);


    @Autowired
    public SearchService(SiteRepository siteRepository, JdbcTemplate jdbcTemplate, AppState appState, ConfigProperties props) {
        this.siteRepository = siteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.appState = appState;
        this.props = props;
        this.cached = false;
        this.cache = new SearchCache();
    }

    public SearchDto doSearch(String query, String siteUrl, int offset, int limit) {
        long start = System.currentTimeMillis();
        if (appState.isIndexing()) {
            return  new SearchDto.Error("Выполняется индексация, поиск временно недоступен");
        }

        String url = siteUrl == null ? "all" : siteUrl;
        if (query.isEmpty()) {
            throw new IllegalArgumentException("Задан пустой поисковый запрос");
        }

        if (cached && query.equals(cache.getLastQuery()) && url.equals(cache.getSite())) {
            return new SearchDto.Success(cache.getCacheCollection().size(),
                    sortResultsByRelevance(cache.getCacheCollection(), limit, offset));
        }
        int siteId = siteUrl == null ? -1 : siteRepository.findByUrl(siteUrl).getId();

        List<SearchResultDto> results = performSearch(query, siteId);
        if (results.size() == 0) {
            return new SearchDto.Error("По данному запросу ничего не найдено: " + query);
        }
        limit = limit == 0 ? 20 : limit;
        List<SearchResultDto> data = sortResultsByRelevance(results, limit, offset);
        setCacheValues(query, url, results);

        LOGGER.info("Doing search took " + (System.currentTimeMillis() - start));

        return new SearchDto.Success(results.size(), data);
    }


    private List<SearchResultDto> performSearch(String query, int siteId) {

        List<FoundPage> foundPages;
        try {
            foundPages = getFoundPages(query, siteId);
        }catch (NullPointerException e){
            LOGGER.info(e);
            return new ArrayList<>();
        }

        List<SearchResultDto> results = new ArrayList<>();
        for (FoundPage fp : foundPages) {
            int foundPageSiteId = fp.getSiteId();
            Site fromDb = siteRepository.findById(foundPageSiteId).get();
            SearchResultDto srd = new SearchResultDto(
                    fromDb.getUrl(), fromDb.getName(), fp.getUri(),
                    fp.getTitle(), fp.getSnippet(), fp.getRelevance());
            results.add(srd);
        }

        return results;
    }

    private List<SearchResultDto> sortResultsByRelevance(List<SearchResultDto> results, int limit, int offset) {
        return results.stream()
                .distinct()
                .sorted(Comparator.comparing(SearchResultDto::getRelevance).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<FoundPage> getFoundPages(String query, int siteId) {
        SearchHelper helper = null;
        float threshold = props.getFrequencyThreshold();
        try {
            helper = new SearchHelper(query, siteId, jdbcTemplate, threshold);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return helper.getFoundPages();
    }

    private void setCacheValues(String query, String site, List<SearchResultDto> results) {
        cache.setLastQuery(query);
        cache.setSite(site);
        cache.setCacheCollection(results);
        cached = true;
    }
}

