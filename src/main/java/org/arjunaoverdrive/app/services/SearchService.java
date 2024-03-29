package org.arjunaoverdrive.app.services;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.DAO.SiteRepository;
import org.arjunaoverdrive.app.config.AppState;
import org.arjunaoverdrive.app.config.ConfigProperties;
import org.arjunaoverdrive.app.model.Site;
import org.arjunaoverdrive.app.search.EnhancedSearchHelper;
import org.arjunaoverdrive.app.search.FoundPage;
import org.arjunaoverdrive.app.webapp.DTO.SearchDto;
import org.arjunaoverdrive.app.webapp.DTO.SearchResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {
    private final SiteRepository siteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AppState appState;
    private final ConfigProperties props;
    private static final Logger LOGGER = Logger.getLogger(SearchService.class);
    private int resultSize;

    @Autowired
    public SearchService(SiteRepository siteRepository, JdbcTemplate jdbcTemplate, AppState appState, ConfigProperties props) {
        this.siteRepository = siteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.appState = appState;
        this.props = props;
        this.resultSize = 0;
    }

    public SearchDto doSearch(String query, String siteUrl, int offset, int limit) throws IOException {
        long start = System.currentTimeMillis();
        if (appState.isIndexing()) {
            return new SearchDto.Error("Indexing is in progress, search is temporarily unavailable");
        }

        if (query.isEmpty()) {
            throw new IllegalArgumentException("The search query is empty");
        }

        int siteId = siteUrl == null || siteUrl.isEmpty()
                ? -1 : siteRepository.findByUrl(siteUrl).getId();
        List<SearchResultDto> results = performSearch(query, siteId, limit, offset);
        if (results.isEmpty()) {
            return new SearchDto.Error("Nothing is found by the search query: " + query);
        }

        LOGGER.info("Doing search took " + (System.currentTimeMillis() - start) + " ms; query: " + query);
        return new SearchDto.Success(resultSize, results);
    }

    private List<SearchResultDto> performSearch(String query, int siteId, int limit, int offset) throws IOException {
        List<SearchResultDto> results = new ArrayList<>();
        List<FoundPage> foundPages = getFoundPages(query, siteId, limit, offset);

        if (foundPages.isEmpty()) {
            return results;
        }
        populateResultsWithFoundPages(results, foundPages);
        return sortByRelevance(results);
    }

    private void populateResultsWithFoundPages(List<SearchResultDto> results, List<FoundPage> foundPages) {
        for (FoundPage fp : foundPages) {
            int foundPageSiteId = fp.getSiteId();
            Site fromDb = siteRepository.findById(foundPageSiteId).get();

            SearchResultDto srd = new SearchResultDto(
                    fromDb.getUrl(),
                    fromDb.getName(),
                    fp.getUri(),
                    fp.getTitle(),
                    fp.getSnippet(),
                    fp.getRelevance());
            results.add(srd);
        }
    }

    private List<SearchResultDto> sortByRelevance(List<SearchResultDto> results) {
        return results.stream()
                .sorted(Comparator.comparing(SearchResultDto::getRelevance).reversed())
                .collect(Collectors.toList());
    }

    private List<FoundPage> getFoundPages(String query, int siteId, int limit, int offset) throws IOException {
        float threshold = props.getFrequencyThreshold();
        EnhancedSearchHelper helper = new EnhancedSearchHelper(query, siteId, jdbcTemplate, threshold);
        List<FoundPage> foundPages = helper.getFoundPages(limit, offset);
        if (foundPages.isEmpty()) {
            return new ArrayList<>();
        }
        resultSize = helper.getResultsSize();
        return foundPages;
    }
}

