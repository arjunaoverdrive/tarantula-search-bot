package main.app.services;

import main.app.DAO.SiteRepository;
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
    private static final Logger LOGGER = Logger.getLogger(SearchService.class);

    @Autowired
    public SearchService(SiteRepository siteRepository, JdbcTemplate jdbcTemplate) {
        this.siteRepository = siteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.cached = false;
        this.cache = new SearchCache();
    }

    public SearchDto doSearch(String query, String siteUrl, int offset, int limit) {
        if(siteUrl == null) {
            return doSearch(query, offset, limit);
        }

        if(cached && query.equals(cache.getLastQuery()) && siteUrl.equals(cache.getSite())){
            return new SearchDto.Success(cache.getCacheCollection().size(),
                    sortResultsByRelevance(cache.getCacheCollection(), limit, offset));
        }

        List<SearchResultDto> results;
        try {
            results = doSearchOnOneSite(query, siteUrl);
        } catch (Exception e) {
            return new SearchDto.Error(e.getLocalizedMessage());
        }
        if(results.size() == 0){
            return new SearchDto.Error("По данному запросу ничего не найдено: " + query);
        }
        if(limit == 0){
            limit = 20;
        }
        List<SearchResultDto> data = sortResultsByRelevance(results, limit, offset);
        setCacheValues(query, siteUrl, results);

        return new SearchDto.Success(results.size(), data);
    }



    private SearchDto doSearch(String query, int offset, int limit){
        if(cached && cache.getLastQuery().equals(query) && cache.getSite().equals("all")){
            return new SearchDto.Success(cache.getCacheCollection().size(),
                    sortResultsByRelevance(cache.getCacheCollection(), limit, offset));
        }

        List<Site> sites = siteRepository.findAll();
        List<SearchResultDto> results = new ArrayList<>();
        for(Site s : sites){
            try {
                results.addAll(doSearchOnOneSite(query, s.getUrl()));
            } catch (Exception e) {
                return new SearchDto.Error(e.getLocalizedMessage());
            }
        }

        if(results.size() == 0){
            return new SearchDto.Error("По данному запросу ничего не найдено: " + query);
        }

        if(limit == 0){
            limit = 20;
        }
        List<SearchResultDto> data = sortResultsByRelevance(results, limit, offset);
        setCacheValues(query, "all", results);
        return new SearchDto.Success(results.size(), data);
    }

    private List<SearchResultDto> doSearchOnOneSite(String query, String siteUrl)  {
        if(query.isEmpty()){
            throw new RuntimeException("Задан пустой поисковый запрос");
        }

        Site s = siteRepository.findByUrl(siteUrl);
        List<FoundPage> foundPages = getFoundPages(query, s.getId());

        List<SearchResultDto> results = new ArrayList<>();
        for (FoundPage fp : foundPages) {
            SearchResultDto srd = new SearchResultDto(
                    siteUrl, s.getName(), fp.getUri(),
                    fp.getTitle(), fp.getSnippet(), fp.getRelevance());
            results.add(srd);
        }

        return results;
    }

    private List<SearchResultDto> sortResultsByRelevance(List<SearchResultDto> results, int limit, int offset){
         return results.stream()
                .distinct()
                .sorted(Comparator.comparing(SearchResultDto::getRelevance).reversed())
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<FoundPage> getFoundPages(String query, int siteId)  {
        SearchHelper helper = null;
        try {
            helper = new SearchHelper(query, siteId, jdbcTemplate);
        } catch (Exception e) {
            LOGGER.error(e.getLocalizedMessage());
        }
        return helper.getFoundPages();
    }

    private void setCacheValues(String query, String site, List<SearchResultDto>results){
        cache.setLastQuery(query);
        cache.setSite(site);
        cache.setCacheCollection(results);
        cached = true;
    }
}

