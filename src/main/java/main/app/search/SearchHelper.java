package main.app.search;

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
        StringBuilder sql = new StringBuilder("SELECT DISTINCT id, lemma, frequency, site_id FROM lemma WHERE lemma IN (");
        Set<String> words = getStringSet();
        if (words.size() == 0) {
            return "";
        }
        for (String s : words) {
            sql.append("'").append(s).append("'").append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        if (siteId != -1) {
            sql.append(" AND site_id = ").append(siteId);
        }
        return sql.toString();
    }

    private Set<Lemma> getLemmas() {
        List<Lemma> lemmas;
        String sql = createSqlToGetLemmas();
        if (sql.isEmpty()) {
            return new HashSet<>();
        }
        lemmas = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            Lemma l = new Lemma();
            l.setId(rs.getInt("id"));
            l.setLemma(rs.getString("lemma"));
            l.setFrequency(rs.getInt("frequency"));
            l.setSite(rs.getInt("site_id"));
            return l;
        });
        if (lemmas.size() == 0) {
            throw new NullPointerException("No lemmas found. SQL: " + sql + "; Query " + query);
        }
        return new HashSet<>(lemmas);
    }

    private int getMaxFrequency(int siteId) {
        String sql = "SELECT count(id) as `count` FROM page WHERE site_id = " + siteId;
        List<Integer> pageCount =  jdbcTemplate.query(sql, (ResultSet rs, int rowNum) ->
                    rs.getInt("count")
            );

        return pageCount.get(0);
    }

    private List<Lemma> ignoreFrequentLemmas(int siteId) {
        float threshold = getMaxFrequency(siteId) * 0.3f;
        Set<Lemma> lemmas = getLemmas();
        return lemmas.stream()
                .filter(lemma -> lemma.getFrequency() < threshold
                        && lemma.getFrequency() != 0
                        && lemma.getSiteId() == siteId)
                .collect(Collectors.toList());
    }

    private List<Integer> getSiteIds() {
        String sql = "SELECT id from site";
        List<Integer> siteIds = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            int siteId = rs.getInt("id");
            return siteId;
        });
        return siteIds;
    }

    private List<Integer> getLemmasIds() {
        List<Lemma> lemmas = new ArrayList<>();
        if (siteId != -1) {
            lemmas = ignoreFrequentLemmas(siteId);
        } else {
            List<Integer> siteIds = getSiteIds();
            for (int id : siteIds) {
                lemmas.addAll(ignoreFrequentLemmas(id));
            }
        }
        List<Integer> ids = new ArrayList<>();
        for (Lemma l : lemmas) {
            ids.add(l.getId());
        }
        return ids;
    }

    private String createSqlToGetPagesContainingLemmas(Set<Integer> lemmaIds) {
        StringBuilder sql = new StringBuilder("SELECT page_id FROM `index` WHERE lemma_id IN (");
        for (Integer lemmaId : lemmaIds) {
            sql.append(lemmaId).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        return sql.toString();
    }

    private Map<Integer, Integer> getPage2CountMap(Set<Integer> lemmaIds) {
        Map<Integer, Integer> page2count = new HashMap<>();
        if (lemmaIds.size() == 0) {
            return page2count;
        }

        String sql = createSqlToGetPagesContainingLemmas(lemmaIds);
        jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            int id = rs.getInt("page_id");
            return page2count.compute(id, (k, v) -> (v == null) ? 1 : v + 1);
        });

        LOGGER.info("Found " + page2count.size() + " pages containing lemma");
        return page2count;
    }

    private List<Integer> getPageIdsByLemmaId(Set<Integer> lemmaIds) {
        List<Integer> pageIds = new ArrayList<>();
        Map<Integer, Integer> page2count = getPage2CountMap(lemmaIds);

        Optional<Integer> optional = page2count.values().stream().max(Integer::compareTo);
        int count = optional.orElse(0);

        for (Map.Entry<Integer, Integer> e : page2count.entrySet()) {
            if (count != 0) {
                pageIds.add(e.getKey());
            }
        }
        return pageIds;
    }

    private String createSqlToGetIndices() {
        Set<Integer> lemmaIds = new HashSet(getLemmasIds());
        List<Integer> pageIds = getPageIdsByLemmaId(lemmaIds);

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

    private List<Index> getIndices() {
        String sql = createSqlToGetIndices();

        List<Index> indices = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            Index index = new Index();
            index.setLemmaId(rs.getInt("lemma_id"));
            index.setPageId(rs.getInt("page_id"));
            index.setRank(rs.getFloat("rank"));
            return index;
        });

        return indices;
    }

    private Map<Integer, Float> countAbsoluteRelevanceForPages() {
        List<Index> indices = getIndices();
        Map<Integer, Float> res = indices.stream()
                .collect(Collectors.toMap(Index::getPageId, Index::getRank, Float::sum, HashMap::new));
        return res;
    }

    private float getMaxRelevanceFromPagesList(Map<Integer, Float> page2absRelevance) {
        return page2absRelevance.values()
                .stream()
                .max(Float::compareTo)
                .get();
    }

    private Map<Integer, Float> calculateRelevanceForPages() {
        Map<Integer, Float> page2absRelevance = countAbsoluteRelevanceForPages();
        Set<Integer> ids = page2absRelevance.keySet();
        Map<Integer, Float> page2relevance = new HashMap<>();
        float max = getMaxRelevanceFromPagesList(page2absRelevance);
        for (int id : ids) {
            float absRel = page2absRelevance.get(id);
            float rel = absRel / max;
            page2relevance.put(id, rel);
        }
        return page2relevance;
    }

    private String createQuery2getPagesList(Map<Integer, Float> page2relevance) {
        Set<Integer> pagesIds = page2relevance.keySet();
        StringBuilder sql = new StringBuilder("SELECT DISTINCT id, path, content, site_id FROM page WHERE id IN (");

        for (int id : pagesIds) {
            sql.append(id).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        if (siteId != -1) {
            sql.append("AND site_id = ").append(siteId);
        }
        return sql.toString();
    }

    private List<Page> getPages(Map<Integer, Float> page2relevance) {
        String sql = createQuery2getPagesList(page2relevance);

        List<Page> foundPages = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            Page p = new Page();
            p.setId(rs.getInt("id"));
            p.setPath(rs.getString("path"));
            p.setContent(rs.getString("content"));
            p.setSiteId(rs.getInt("site_id"));
            return p;
        });

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
                .filter(e -> !e.is("nav"))
                .filter(e -> !children.contains(e))
                .map(Element::ownText)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        Set<String> lemmasFromQuery = counter.countLemmas(query).keySet();
        for (String s : filtered) {
            Set<String> lemmasFromPage = counter.countLemmas(s).keySet();
            if (lemmasFromPage.containsAll(lemmasFromQuery)) {
                return s;
            }
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
        String withoutExtraTags = parser.removeExtraTags();
        return withoutExtraTags.length() < 200 ? withoutExtraTags : parser.shortenLongSnippet(withoutExtraTags);

    }


    public List<FoundPage> getFoundPages() {
        List<FoundPage> foundPages = new ArrayList<>();
        Map<Integer, Float> page2rel = calculateRelevanceForPages();

        List<Page> pages = getPages(page2rel);
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
