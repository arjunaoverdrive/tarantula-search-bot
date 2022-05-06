package org.igor.klimov.app.search;

import org.igor.klimov.app.webapp.DTO.SearchResultDto;

import java.util.List;

public class SearchCache {
    private String lastQuery = "";
    private List<SearchResultDto> cacheCollection;
    private String site = "";

    public SearchCache() {
    }

    public SearchCache(String lastQuery, List<SearchResultDto> cacheCollection, String site) {
        this.lastQuery = lastQuery;
        this.cacheCollection = cacheCollection;
        this.site = site;
    }

    public String getLastQuery() {
        return lastQuery;
    }

    public void setLastQuery(String lastQuery) {
        this.lastQuery = lastQuery;
    }

    public List<SearchResultDto> getCacheCollection() {
        return cacheCollection;
    }

    public void setCacheCollection(List<SearchResultDto> cacheCollection) {
        this.cacheCollection = cacheCollection;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }
}
