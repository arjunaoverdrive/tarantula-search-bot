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
        long start = System.currentTimeMillis();
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
        LOGGER.info("createSqlToGetLemmas " + (System.currentTimeMillis() - start));
        return sql.toString();
    }

    private Set<Lemma> getLemmas() {
        long start = System.currentTimeMillis();
        List<Lemma> lemmas = new ArrayList<>();
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
        LOGGER.info("getLemmas " + (System.currentTimeMillis() - start));
        return new HashSet<>(lemmas);
    }

    //todo create a class to store the output data for the following sql query:
    // SELECT  i.page_id as page_id, count(page_id) as page_count, l.id as lemma_id, l.frequency as frequency, l.site_id as site_id, i.`rank` as `rank`
    //	from lemma l join `index` i on l.id = i.lemma_id
    //    where lemma_id in (753, 12407, 24296, 33381, 11275, 23104, 26098, 39158) group by page_id having count(page_id) = 2 ORDER BY page_count desc;
    //fields: int page_id, int lemma_id, int frequency, int site_id, float rank

    private int getMaxFrequency(int siteId) {
        long start = System.currentTimeMillis();
        String sql = "SELECT count(id) as `count` FROM page WHERE site_id = " + siteId;
        List<Integer> pageCount = null;
        try {
            pageCount = jdbcTemplate.query(sql, (ResultSet rs, int rowNum) ->
                    rs.getInt("count")
            );
        } catch (Exception e) {
            LOGGER.warn(e);
        }
        LOGGER.info("getMaxFrequency " + (System.currentTimeMillis() - start));
        return pageCount.get(0);
    }

    private List<Lemma> ignoreFrequentLemmas(int siteId) {
        long start = System.currentTimeMillis();
        float threshold = getMaxFrequency(siteId) * 0.5f;
        Set<Lemma> lemmas = getLemmas();
        List<Lemma> res = lemmas.stream()
                .filter(lemma -> lemma.getFrequency() < threshold && lemma.getFrequency() != 0)
                .collect(Collectors.toList());
        LOGGER.info("ignoreFrequentLemmas " + (System.currentTimeMillis() - start));
        return res;
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
        long start = System.currentTimeMillis();
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
        LOGGER.info("getLemmasIds " + (System.currentTimeMillis() - start));
        return ids.stream().sorted().collect(Collectors.toList());
    }

    private String createSqlToGetPagesContainingLemmas(Set<Integer> lemmaIds) {
        long start = System.currentTimeMillis();
        StringBuilder sql = new StringBuilder("SELECT page_id FROM `index` WHERE lemma_id IN (");
        for (Integer lemmaId : lemmaIds) {
            sql.append(lemmaId).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        LOGGER.info("createSqlToGetPagesContainingLemmas " + (System.currentTimeMillis() - start));
        return sql.toString();
    }

    private Map<Integer, Integer> getPage2CountMap(Set<Integer> lemmaIds) {
        long start = System.currentTimeMillis();
        Map<Integer, Integer> page2count = new HashMap<>();
        if (lemmaIds.size() == 0) {
            return page2count;
        }

        String sql = createSqlToGetPagesContainingLemmas(lemmaIds);
        jdbcTemplate.query(sql, (ResultSet rs, int rowNum) -> {
            int id = rs.getInt("page_id");
            return page2count.compute(id, (k, v) -> (v == null) ? 1 : v + 1);
        });

        LOGGER.info("getPage2CountMap " + (System.currentTimeMillis() - start));
        return page2count;
    }

    private List<Integer> getPageIdsByLemmaId(Set<Integer> lemmaIds) {
        long start = System.currentTimeMillis();
        List<Integer> pageIds = new ArrayList<>();
        Map<Integer, Integer> page2count = getPage2CountMap(lemmaIds);

        Optional<Integer> optional = page2count.values().stream().max(Integer::compareTo);
        int count = optional.orElse(0);

        for (Map.Entry<Integer, Integer> e : page2count.entrySet()) {
            if (e.getValue() == count  && count != 0) {
                pageIds.add(e.getKey());
            }
        }
        LOGGER.info("getPageIdsByLemmaId " + (System.currentTimeMillis() - start));
        return pageIds;
    }

    private String createSqlToGetIndices() throws PagesNotFoundException {
        long start = System.currentTimeMillis();
        Set<Integer> lemmaIds = new HashSet( getLemmasIds());
        List<Integer> pageIds = getPageIdsByLemmaId(lemmaIds);
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
        LOGGER.info("createSqlToGetIndices " + (System.currentTimeMillis() - start));
        return sql.toString();
    }

    private List<Index> getIndices() throws PagesNotFoundException {
        long start = System.currentTimeMillis();
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
        LOGGER.info("getIndices " + (System.currentTimeMillis() - start));
        return indices;
    }

    private Map<Integer, Float> countAbsoluteRelevanceForPages() throws PagesNotFoundException {
        long start = System.currentTimeMillis();
        List<Index> indices = getIndices();
        Map<Integer, Float> res = indices.stream()
                .collect(Collectors.toMap(Index::getPageId, Index::getRank, Float::sum, HashMap::new));
        LOGGER.info("countAbsoluteRelevanceForPages " + (System.currentTimeMillis() - start));
        return res;
    }

    private float getMaxRelevanceFromPagesList(Map<Integer, Float> page2absRelevance) {
        long start = System.currentTimeMillis();
        float maxRelevance = page2absRelevance.values()
                .stream()
                .max(Float::compareTo)
                .get();
        LOGGER.info("getMaxRelevanceFromPagesList " + (System.currentTimeMillis() - start));
        return maxRelevance;
    }

    private Map<Integer, Float> calculateRelevanceForPages() throws PagesNotFoundException {
        long start = System.currentTimeMillis();
        Map<Integer, Float> page2absRelevance = countAbsoluteRelevanceForPages();
        Set<Integer> ids = page2absRelevance.keySet();
        Map<Integer, Float> page2relevance = new HashMap<>();
        float max = getMaxRelevanceFromPagesList(page2absRelevance);
        for (int id : ids) {
            float absRel = page2absRelevance.get(id);
            float rel = absRel / max;
            page2relevance.put(id, rel);
        }
        LOGGER.info("calculateRelevanceForPages " + (System.currentTimeMillis() - start));
        return page2relevance;
    }

    private String createQuery2getPagesList(Map<Integer, Float> page2relevance) throws PagesNotFoundException {
        long start = System.currentTimeMillis();
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
        LOGGER.info("createQuery2getPagesList " + (System.currentTimeMillis() - start));
        return sql.toString();
    }

    private List<Page> getPages(Map<Integer, Float> page2relevance) {
        long start = System.currentTimeMillis();
        List<Page> foundPages = new ArrayList<>();
        try {
            String sql = createQuery2getPagesList(page2relevance);

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
        LOGGER.info("getPages " + (System.currentTimeMillis() - start));
        return foundPages;
    }

    private String getTitle(String html) {
        long start = System.currentTimeMillis();
        String title = Jsoup.parse(html).select("title").text();
        LOGGER.info("getTitle " + (System.currentTimeMillis() - start));
        return title;
    }

    private String getPageElementWithOwnTextContainingQueryLemmas(String html) {
        long start = System.currentTimeMillis();
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
            if (lemmasFromPage.containsAll(lemmasFromQuery)) {
                LOGGER.info("getPageElementWithOwnTextContainingQueryLemmas " + (System.currentTimeMillis() - start));
                return s;
            }
        }

        return "Lemmas from query are not present within the same element " + query;
    }


    private String createSnippet(String html) throws RuntimeException {
        long start = System.currentTimeMillis();
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
        String res = withoutExtraTags.length() < 200 ? withoutExtraTags : parser.shortenLongSnippet(withoutExtraTags);
        LOGGER.info("createSnippet " + (System.currentTimeMillis() - start));
        return res;
    }


    public List<FoundPage> getFoundPages() {
        long start = System.currentTimeMillis();
        List<FoundPage> foundPages = new ArrayList<>();
        Map<Integer, Float> page2rel;
        try {
            page2rel = calculateRelevanceForPages();
        } catch (PagesNotFoundException e) {
            LOGGER.info(e.getMessage());
            return new ArrayList<>();
        }
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
        LOGGER.info("getFoundPages " + (System.currentTimeMillis() - start));
        return foundPages;
    }
}
