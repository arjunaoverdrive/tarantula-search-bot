package org.igor.klimov.app.services;

import org.apache.log4j.Logger;
import org.igor.klimov.app.DAO.SiteRepository;
import org.igor.klimov.app.config.AppState;
import org.igor.klimov.app.config.ConfigProperties;
import org.igor.klimov.app.model.Site;
import org.igor.klimov.app.search.EnhancedSearchHelper;
import org.igor.klimov.app.search.FoundPage;
import org.igor.klimov.app.webapp.DTO.SearchDto;
import org.igor.klimov.app.webapp.DTO.SearchResultDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    public SearchDto doSearch(String query, String siteUrl, int offset, int limit) {
        long start = System.currentTimeMillis();
        if (appState.isIndexing()) {
            return new SearchDto.Error("Indexing is in progress, search is temporarily unavailable");
        }

        if (query.isEmpty()) {
            throw new IllegalArgumentException("The search query is empty");
        }

        int siteId = siteUrl == null ? -1 : siteRepository.findByUrl(siteUrl).getId();

        List<SearchResultDto> results = performSearch(query, siteId, limit, offset);
        if (results.size() == 0) {
            return new SearchDto.Error("Nothing is found by the search query: " + query);
        }

        LOGGER.info("Doing search took " + (System.currentTimeMillis() - start) + "ms; query: " + query);
        return new SearchDto.Success(resultSize, results);
    }

    private List<SearchResultDto> performSearch(String query, int siteId, int limit, int offset) {

        List<FoundPage> foundPages = getFoundPages(query, siteId, limit, offset);

        List<SearchResultDto> results = new ArrayList<>();

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

        return sortByRelevance(results);
    }

    private List<SearchResultDto> sortByRelevance(List<SearchResultDto> results){
        return results.stream()
                .sorted(Comparator.comparing(SearchResultDto::getRelevance).reversed())
                .collect(Collectors.toList());
    }


    private List<FoundPage> getFoundPages(String query, int siteId, int limit, int offset) {
        float threshold = props.getFrequencyThreshold();

        EnhancedSearchHelper helper = null;
        List<FoundPage> foundPages = new ArrayList<>();

        try {
            helper = new EnhancedSearchHelper(query, siteId, jdbcTemplate, threshold);
            foundPages = helper.getFoundPages(limit, offset);
        } catch (IOException e) {
            LOGGER.error(e);
        }

        resultSize = helper.getResultsSize();

        return foundPages;
    }

}

