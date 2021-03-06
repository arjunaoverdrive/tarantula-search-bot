package org.igor.klimov.app.search;

import org.apache.log4j.Logger;
import org.igor.klimov.app.lemmatizer.LangToCounter;
import org.igor.klimov.app.lemmatizer.Language;
import org.igor.klimov.app.lemmatizer.LemmaCounter;
import org.igor.klimov.app.model.Lemma;
import org.igor.klimov.app.model.Page;
import org.igor.klimov.app.search.snippetParser.SnippetCreator;
import org.igor.klimov.app.search.snippetParser.SnippetParser;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EnhancedSearchHelper implements SearchHelper {
    private final String query;
    private final int siteId;
    private final LemmaCounter counter;
    private final JdbcTemplate jdbcTemplate;
    private final float threshold;
    private final SearchSqlBuilder sqlBuilder;
    private int resultsSize;

    private final Logger LOGGER = Logger.getLogger(EnhancedSearchHelper.class);


    public EnhancedSearchHelper(String query, int siteId, JdbcTemplate jdbcTemplate, float threshold) throws IOException {
        this.query = query;
        this.siteId = siteId;
        this.jdbcTemplate = jdbcTemplate;
        this.threshold = threshold;

        this.sqlBuilder = new SearchSqlBuilder();
        this.resultsSize = 0;
        this.counter = getLemmaCounter();
    }

    private LemmaCounter getLemmaCounter() {
        LangToCounter langToCounter = LangToCounter.getInstance();
        Language language = getQueryLanguage();
        return langToCounter.getLemmaCounter(language.getLanguage());
    }

    public int getResultsSize() {
        return resultsSize;
    }

    private void setResultsSize(List<Integer> pageToCount) {
        resultsSize = pageToCount.size();
    }

    public List<FoundPage> getFoundPages(int limit, int offset) {

        List<FoundPage> foundPages = new ArrayList<>();

        List<Lemma> queryLemmasList = getListOfUniqueLemmas();
        if(queryLemmasList.isEmpty()){
            return foundPages;
        }
        List<Lemma> uniqueLemmas = getLemmasWithinThreshold(queryLemmasList);

        Map<Integer, Float> pageToRelevance = pageToAbsoluteRelevanceMap(limit, offset, uniqueLemmas);

        Set<Integer> pageIds = pageToRelevance.keySet();

        List<Page> pages = getResultPages(pageIds);

        pages.forEach(p -> {
            float relevance = pageToRelevance.get(p.getId());
            foundPages.add(createFoundPage(p, relevance, uniqueLemmas));
        });

        return foundPages;
    }

    private FoundPage createFoundPage(Page page, float relevance, List<Lemma> queryLemmas) {

        int siteId = page.getSiteId();
        String uri = page.getPath();
        String title = getPageTitle(page);
        String snippet = createPageSnippet(page, queryLemmas);

        FoundPage foundPage = new FoundPage(siteId, uri, title, snippet, relevance);
        return foundPage;
    }


    private Language getQueryLanguage() {
        Pattern pattern = Pattern.compile("[??-??]+.*");

        return pattern.matcher(query).matches() ? Language.RUSSIAN : Language.ENGLISH;
    }

    private String createPageSnippet(Page page, List<Lemma> queryLemmas) {

        String html = page.getContent();

        List<String> distinctLemmasFromQuery = getDistinctQueryLemmas(queryLemmas);

        SnippetParser snippetParser =
                new SnippetCreator(distinctLemmasFromQuery, html, counter);

        String s = snippetParser.create();
        return s;
    }

    private List<String> getDistinctQueryLemmas(List<Lemma> queryLemmas) {
        return queryLemmas.stream()
                .map(Lemma::getLemma)
                .distinct()
                .collect(Collectors.toList());
    }

    private String getPageTitle(Page page) {

        return Jsoup.parse(page.getContent())
                .select("title")
                .text();
    }


    private List<Page> getResultPages(Set<Integer> pageIds) {

        String sqlToGetResultPages = sqlBuilder.createSqlToGetResultPages(pageIds);

        List<Page> pages = jdbcTemplate.query(sqlToGetResultPages, (ResultSet rs, int rowNum) -> {
            Page p = new Page();
            p.setId(rs.getInt("id"));
            p.setPath(rs.getString("path"));
            p.setCode(rs.getInt("code"));
            p.setContent(rs.getString("content"));
            p.setSiteId(rs.getInt("site_id"));
            return p;
        });

        return pages;
    }


    private Map<Integer, Float> pageToAbsoluteRelevanceMap(int limit, int offset, List<Lemma> uniqueLemmas) {
        Map<Integer, Float> pageToAbsoluteRelevance = new HashMap<>();

        Map<Integer, Float> pageToRelevance = getPageToRelevanceMap(limit, offset, uniqueLemmas);

        float maxRelevance = pageToRelevance
                .values()
                .stream()
                .max(Float::compare)
                .orElseGet(() -> pageToRelevance
                        .values()
                        .stream()
                        .findFirst()
                        .get());

        pageToRelevance
                .forEach((key, value) -> pageToAbsoluteRelevance.put(key, value / maxRelevance));

        return pageToAbsoluteRelevance;
    }


    private Map<Integer, Float> getPageToRelevanceMap(int limit, int offset, List<Lemma> uniqueLemmas) {

        List<Integer> idsOfUniqueLemmasWithinThreshold =
                getIdsOfLemmasWithinThreshold(uniqueLemmas);

        Map<Integer, Float> pageToRelevance = new HashMap<>();

        Map<Integer, List<Integer>> siteIdsToListOfUniqueLemmasMap = getSiteToUniqueLemmasIdsMap(uniqueLemmas);

        List<Integer> pageIds = getIdsForPagesContainingAllLemmas(siteIdsToListOfUniqueLemmasMap);

        String sqlToGetPageToRelevanceMap =
                sqlBuilder.createSqlToGetPageToRelevanceMap(idsOfUniqueLemmasWithinThreshold, pageIds, limit, offset);

        jdbcTemplate.query(sqlToGetPageToRelevanceMap, (ResultSet rs, int rowNum) ->
                pageToRelevance.put(rs.getInt("page_id"), rs.getFloat("relevance"))
        );

        return pageToRelevance;
    }

    private Map<Integer, List<Integer>> getSiteToUniqueLemmasIdsMap(List<Lemma>uniqueLemmas){
        Map<Integer, List<Integer>> siteIdToListOfUniqueLemmas = new HashMap<>();

        for(Lemma l : uniqueLemmas){
            int siteId = l.getSiteId();
            if(siteIdToListOfUniqueLemmas.get(siteId) == null){
                siteIdToListOfUniqueLemmas.put(siteId, List.of(l.getId()));
            } else {
                List<Integer> values = siteIdToListOfUniqueLemmas.get(siteId);
                List<Integer> list = new ArrayList<>(values);
                list.add(l.getId());
                siteIdToListOfUniqueLemmas.put(siteId, list);
            }
        }

        return siteIdToListOfUniqueLemmas;
    }


    private List<Integer> getIdsForPagesContainingAllLemmas(Map<Integer, List<Integer>> siteIdToUniqueLemmas) {
        String sqlToGetPagesContainingLemmas = sqlBuilder.createSqlToGetPagesContainingLemmas(siteIdToUniqueLemmas);

        List<Integer> pagesWithLemmas = jdbcTemplate.query(sqlToGetPagesContainingLemmas,
                (ResultSet rs, int rowNum) -> rs.getInt("page_id"));

        setResultsSize(pagesWithLemmas);

        return pagesWithLemmas;
    }


    private List<Lemma> getLemmasWithinThreshold(List<Lemma> uniqueLemmas) {
        Map<Integer, Integer> siteToPagesCount = getPagesCountForSites();

        Map<Integer, Float> siteToThreshold = getThresholdForSites(siteToPagesCount);

        List<Lemma> lemmasNotExceedingThreshold = new ArrayList<>();

        for (Lemma l : uniqueLemmas) {
            if (l.getFrequency() < siteToThreshold.get(l.getSiteId())) {
                lemmasNotExceedingThreshold.add(l);
            }
        }

        return lemmasNotExceedingThreshold;
    }

    private List<Integer> getIdsOfLemmasWithinThreshold(List<Lemma> lemmas) {

        return lemmas.stream()
                .map(Lemma::getId)
                .collect(Collectors.toList());
    }

    private List<Lemma> getListOfUniqueLemmas() {
        List<Lemma> uniqueLemmas = null;
        Set<String> queryLemmas = getQueryLemmasSet();

        String sqlToGetLemmas = sqlBuilder.createSqlToGetLemmas(queryLemmas, siteId);
        try {
            uniqueLemmas = jdbcTemplate.query(sqlToGetLemmas, (ResultSet rs, int rowNum) -> {
                Lemma l = new Lemma();
                l.setId(rs.getInt("id"));
                l.setLemma(rs.getString("lemma"));
                l.setFrequency(rs.getInt("frequency"));
                l.setSite(rs.getInt("site_id"));
                return l;
            });
        } catch (Exception e) {
            LOGGER.warn(sqlToGetLemmas + " " + e);
        }

        return uniqueLemmas;
    }


    private Set<String> getQueryLemmasSet() {
        return counter.countLemmas(query).keySet();
    }


    private Map<Integer, Integer> getPagesCountForSites() {
        Map<Integer, Integer> siteToPagesCount = new HashMap<>();

        String sqlToGetSiteToPagesCount = sqlBuilder.createSqlToGetPageCountForSites(siteId);

        jdbcTemplate.query(sqlToGetSiteToPagesCount, (ResultSet rs, int rowNum) ->
                siteToPagesCount.put(rs.getInt("site_id"), rs.getInt("page_count")));

        return siteToPagesCount;
    }


    private Map<Integer, Float> getThresholdForSites(Map<Integer, Integer> siteToPagesCount) {

        Map<Integer, Float> siteToThreshold = new HashMap<>();

        for (Map.Entry<Integer, Integer> e : siteToPagesCount.entrySet()) {
            siteToThreshold.put(e.getKey(), e.getValue() * threshold);
        }

        return siteToThreshold;
    }
}
