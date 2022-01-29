package main.app.search;

import main.app.exceptions.PagesNotFoundException;
import main.app.lemmatizer.LemmaCounter;
import main.app.model.Index;
import main.app.model.Lemma;
import main.app.model.Page;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

public class SearchHelper {
    private final String query;
    private final int siteId;
    private final LemmaCounter counter;
    private final JdbcTemplate jdbcTemplate;
    private final Logger LOGGER = Logger.getLogger(SearchHelper.class);

    public SearchHelper(String query, int siteId, JdbcTemplate jdbcTemplate) throws IOException {
        this.query = query;
        this.siteId = siteId;
        this.jdbcTemplate = jdbcTemplate;
        this.counter = new LemmaCounter();
    }

    private Set<String> getStringSet() {
        return counter.countLemmas(query).keySet();
    }

    private String createSqlToGetLemmas() {
        StringBuilder sql = new StringBuilder("SELECT id, lemma, frequency FROM lemma WHERE lemma IN (");
        Set<String> words = getStringSet();
        if (words.size() == 0) {
            return "";
        }
        for (String s : words) {
            sql.append("'").append(s).append("'").append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(") AND site_id = ").append(siteId);
        return sql.toString();
    }

    private List<Lemma> getLemmas() {
        List<Lemma> lemmas = new ArrayList<>();
        String sql = createSqlToGetLemmas();
        if (sql.isEmpty()) {
            return lemmas;
        }
        lemmas = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            Lemma l = new Lemma();
            l.setId(rs.getInt("id"));
            l.setLemma(rs.getString("lemma"));
            l.setFrequency(rs.getInt("frequency"));
            return l;
        });
        return lemmas;
    }

    private int getMaxFrequency() {
        String sql = "SELECT count(id) as `count` FROM page WHERE site_id = " + siteId;
        List<Integer> pageCount = null;
        try {
            pageCount = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) ->
                    rs.getInt("count")
            );
        } catch (Exception e) {
            LOGGER.warn(e);
        }
        return pageCount.get(0);
    }

    private List<Lemma> ignoreFrequentLemmas() {
        float threshold = getMaxFrequency() * 0.5f;
        List<Lemma> lemmas = getLemmas();
        return lemmas
                .stream()
                .filter(lemma -> lemma.getFrequency() < threshold && lemma.getFrequency() != 0)
                .collect(Collectors.toList());
    }


    private List<Integer> getLemmasIds() {
        List<Lemma> lemmas = ignoreFrequentLemmas();
        List<Integer> ids = new ArrayList<>();
        for (Lemma l : lemmas) {
            ids.add(l.getId());
        }
        return ids.stream().sorted().collect(Collectors.toList());
    }

    private String createSqlToGetPagesContainingLemmas(List<Integer> lemmaIds) {
        StringBuilder sql = new StringBuilder("SELECT page_id FROM `index` WHERE lemma_id IN (");
        for (Integer lemmaId : lemmaIds) {
            sql.append(lemmaId).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        return sql.toString();
    }

    private Map<Integer, Integer> getPage2CountMap() {
        List<Integer> lemmaIds = getLemmasIds();
        Map<Integer, Integer> page2count = new HashMap<>();
        if (lemmaIds.size() == 0) {
            return page2count;
        }

        String sql = createSqlToGetPagesContainingLemmas(lemmaIds);
        jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            int id = rs.getInt("page_id");
            return page2count.compute(id, (k, v) -> (v == null) ? 1 : v + 1);
        });

        return page2count;
    }

    private List<Integer> getPageIdsByLemmaId() {
        List<Integer> pageIds = new ArrayList<>();
        int count = getStringSet().size();
        Map<Integer, Integer> page2count = getPage2CountMap();
        for (Map.Entry<Integer, Integer> e : page2count.entrySet()) {
            if (e.getValue() == count) {
                pageIds.add(e.getKey());
            }
        }
        return pageIds;
    }

    private String createSqlToGetIndices() throws PagesNotFoundException {
        List<Integer> lemmaIds = getLemmasIds();
        List<Integer> pageIds = getPageIdsByLemmaId();
        if (pageIds.size() == 0) {
            throw new PagesNotFoundException("По данному запросу ничего не найдено: " + query);
        }
        StringBuilder sql = new StringBuilder("SELECT lemma_id, page_id, `rank` FROM `index` WHERE lemma_id IN (");
        for (Integer lemmaId : lemmaIds) {
            sql.append(lemmaId).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(") AND page_id IN (");
        for (Integer pageId : pageIds) {
            sql.append(pageId).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        return sql.toString();
    }

    private List<Index> getIndices() throws PagesNotFoundException {
        String sql = createSqlToGetIndices();
        List<Index> indices = new ArrayList<>();
        try {
            indices = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
                Index index = new Index();
                index.setLemmaId(rs.getInt("lemma_id"));
                index.setPageId(rs.getInt("page_id"));
                index.setRank(rs.getFloat("rank"));
                return index;
            });
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return indices;
    }

    private Map<Integer, Float> countAbsoluteRelevanceForPages() throws PagesNotFoundException {
        List<Index> indices = getIndices();
        return indices.stream()
                .collect(Collectors.toMap(Index::getPageId, Index::getRank, Float::sum, HashMap::new));
    }

    private float getMaxRelevanceFromPagesList() throws PagesNotFoundException {
        Map<Integer, Float> page2absoluteRelevance = countAbsoluteRelevanceForPages();
        return page2absoluteRelevance.values()
                .stream()
                .max(Float::compareTo)
                .get();
    }

    private Map<Integer, Float> calculateRelevanceForPages() throws PagesNotFoundException {
        Map<Integer, Float> page2absRelevance = countAbsoluteRelevanceForPages();
        Set<Integer> ids = page2absRelevance.keySet();
        Map<Integer, Float> page2relevance = new HashMap<>();
        float max = getMaxRelevanceFromPagesList();
        for (int id : ids) {
            float absRel = page2absRelevance.get(id);
            float rel = absRel / max;
            page2relevance.put(id, rel);
        }
        return page2relevance;
    }

    private String createQuery2getPagesList() throws PagesNotFoundException {
        Set<Integer> pagesIds = calculateRelevanceForPages().keySet();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT id, path, content, site_id FROM page WHERE id IN (");
        for (int id : pagesIds) {
            sql.append(id).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(") AND site_id = ").append(siteId);
        return sql.toString();
    }

    private List<Page> getPages() {
        List<Page> foundPages = new ArrayList<>();

        try {
            String sql = createQuery2getPagesList();

            foundPages = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
                Page p = new Page();
                p.setId(rs.getInt("id"));
                p.setPath(rs.getString("path"));
                p.setContent(rs.getString("content"));
                p.setSiteId(rs.getInt("site_id"));

                return p;
            });
        } catch (PagesNotFoundException e) {
            LOGGER.info(e.getMessage());
        }
        return foundPages;
    }

    private String getTitle(String html) {
        return Jsoup.parse(html).select("title").text();
    }


    private String getPageElementWithOwnTextContainingQueryLemmas(String html) {
        Element body = Jsoup.parse(html).body();

        Elements navChildren = body.select("nav");
        List<Element> children = new ArrayList<>();
        for (Element e : navChildren) {
            children.addAll(e.children());
        }

        List<Element> elements = body.getAllElements();
        List<String> filtered = elements.stream()
                .filter(e -> !e.is("head"))
                .filter(e -> !e.is("nav"))
                .filter(e -> !children.contains(e))
                .filter(Element::hasText)
                .map(Element::ownText)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        Set<String> lemmasFromQuery = counter.countLemmas(query).keySet();
        for (String s : filtered) {
            Set<String> lemmasFromPage = counter.countLemmas(s).keySet();
            if (lemmasFromPage.containsAll(lemmasFromQuery))
                return s;
        }

        return "Lemmas from query are not present within the same element " + query;
    }


    private String createSnippet(String html) throws RuntimeException {
        SnippetParser parser = null;
        String snippet = getPageElementWithOwnTextContainingQueryLemmas(html);
        if (snippet.equals("Lemmas from query are not present within the same element " + query)) {
            throw new RuntimeException(snippet);
        }
        try {
            parser = new SnippetParser(query, snippet, counter);
        } catch (IOException e) {
            LOGGER.error(e);
        }
        String res = parser.removeExtraTags();
        return res.length() < 200 ? res : parser.shortenLongSnippet(res);
    }


    public List<FoundPage> getFoundPages() {
        List<FoundPage> foundPages = new ArrayList<>();
        Map<Integer, Float> page2rel;
        try {
            page2rel = calculateRelevanceForPages();
        } catch (PagesNotFoundException e) {
            LOGGER.info(e.getMessage());
            return new ArrayList<>();
        }
        List<Page> pages = getPages();
        for (Page p : pages) {
            int siteId = p.getSiteId();
            String uri = p.getPath();
            String pageText = p.getContent();
            String title = getTitle(pageText);
            String snippet;
            try {
                snippet = createSnippet(pageText);
            } catch (RuntimeException e) {
                LOGGER.warn(e + " " + p.getId() + " " + p.getPath());
                continue;
            }
            float relevance = page2rel.get(p.getId());
            FoundPage foundPage = new FoundPage(siteId, uri, title, snippet, relevance);
            foundPages.add(foundPage);
        }
        return foundPages;
    }
}
