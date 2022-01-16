package main.app.search;

import main.app.config.DbConnection;
import main.app.model.Index;
import main.app.model.Lemma;
import main.app.model.Page;
import main.app.exceptions.PagesNotFoundException;
import main.app.lemmatizer.LemmaCounter;
import org.apache.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SearchHelper {
    private String query;
    private final LemmaCounter counter;
    private final Logger LOGGER = Logger.getLogger(SearchHelper.class);

    public SearchHelper(String query) throws IOException {
        this.query = query;
        this.counter = new LemmaCounter();
    }

    private Set<String> getStringSet() {
        return counter.countLemmas(query).keySet();
    }

    private String createSqlToGetLemmas() {
        StringBuilder sql = new StringBuilder("SELECT id, lemma, frequency FROM lemma WHERE lemma IN (");
        Set<String> words = getStringSet();
        if (words.size() == 0){
            return "";
        }
        for (String s : words) {
            sql.append("'").append(s).append("'").append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        return sql.toString();
    }

    private List<Lemma> getLemmas() {
        List<Lemma> lemmas = new ArrayList<>();
        String sql = createSqlToGetLemmas();
        if(sql.isEmpty()) {
            return lemmas;
        }
        try {
            ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(sql);
            while (rs.next()) {
                Lemma l = new Lemma();
                l.setId(rs.getInt("id"));
                l.setLemma(rs.getString("lemma"));
                l.setFrequency(rs.getInt("frequency"));
                lemmas.add(l);
            }
        } catch (SQLException e) {
            LOGGER.error(e);
        }
        return lemmas;
    }

    private int getMaxFrequency() {
        String sql = "SELECT count(id) as `count` FROM page";
        int pageCount = 0;
        try {
            ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(sql);
            while (rs.next()) {
                pageCount = rs.getInt("count");
            }
        } catch (SQLException e) {
            LOGGER.error(e);
        }
        return pageCount;
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
        return ids;
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
        if (lemmaIds.size() == 0){
            return page2count;
        }
        String sql = createSqlToGetPagesContainingLemmas(lemmaIds);
        try {
            ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(sql);
            while (rs.next()) {
                int id = rs.getInt("page_id");
                page2count.compute(id, (k, v) -> (v == null) ? 1 : v + 1);
            }
        } catch (SQLException e) {
            LOGGER.error(e);
        }
        return page2count;
    }

    private List<Integer> getPageIdsByLemmaId() {
        List<Integer> lemmaIds = getLemmasIds();
        List<Integer> pageIds = new ArrayList<>();
        int count = lemmaIds.size();
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
            throw new PagesNotFoundException("Pages containing lemmas from query not found");
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
            ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(sql);
            while (rs.next()) {
                Index index = new Index();
                index.setLemmaId(rs.getInt("lemma_id"));
                index.setPageId(rs.getInt("page_id"));
                index.setRank(rs.getFloat("rank"));
                indices.add(index);
            }
        } catch (SQLException e) {
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
        StringBuilder sql = new StringBuilder("SELECT id, path, content FROM page WHERE id IN (");
        for (int id : pagesIds) {
            sql.append(id).append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        return sql.toString();
    }

    private List<Page> getPages() {
        List<Page> foundPages = new ArrayList<>();
        try {
            ResultSet rs = DbConnection.getConnection().createStatement()
                    .executeQuery(createQuery2getPagesList());
            while (rs.next()) {
                Page p = new Page();
                p.setId(rs.getInt("id"));
                p.setPath(rs.getString("path"));
                p.setContent(rs.getString("content"));
                foundPages.add(p);
            }
        } catch (SQLException e) {
            LOGGER.error(e);
        } catch (PagesNotFoundException e) {
            LOGGER.warn(e.getMessage());
        }
        return foundPages;
    }

    private String getTitle(String html) {
        return Jsoup.parse(html).select("title").text();
    }

    private String getQueryRegex() {
        String[] strings = query.split(" ");
        List<String> stringsList = Arrays.stream(strings)
                .filter(s -> s.length() > 2)
                .map(s -> s.replaceAll("[^А-Яа-я]", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder("(?s).*");
        for (String s : stringsList) {
            builder.append(s).append(".*");
        }
        return builder.toString();
    }

    private String getPageElementWithOwnTextMatchingRegex(String html) {
        Document doc = Jsoup.parse(html);
        String queryPattern = getQueryRegex();
        List<Element> elements = doc.getAllElements();
        Optional<String> optional = elements.stream()
                .map(Element::ownText)
                .filter(s -> s.matches(queryPattern))
                .findFirst();
        return optional.orElseThrow(() -> new NoSuchElementException("No exact match was found"));
    }


    private String createSnippet(String html) {
        SnippetParser parser = null;
        try {
            parser = new SnippetParser(query, getPageElementWithOwnTextMatchingRegex(html));
        } catch (NoSuchElementException e) {
            String pageText = Jsoup.parse(html).text();
            try {
                parser = new SnippetParser(query, pageText);
            } catch (IOException ioe) {
                LOGGER.warn(ioe);
            }
            String res = parser.removeExtraTags();
            return res.length() < 500 ? res : parser.shortenLongSnippet(res);
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return parser.removeExtraTags();
    }


    private List<FoundPage> getFoundPages() {
        List<FoundPage> foundPages = new ArrayList<>();
        Map<Integer, Float> page2rel;
        try {
            page2rel = calculateRelevanceForPages();
        } catch (PagesNotFoundException e) {
            LOGGER.warn(e.getMessage());
            return new ArrayList<>();
        }
        List<Page> pages = getPages();
        for (Page p : pages) {
            String uri = p.getPath();
            String pageText = p.getContent();
            String title = getTitle(pageText);
            String snippet = createSnippet(pageText);
            float relevance = page2rel.get(p.getId());
            FoundPage foundPage = new FoundPage(uri, title, snippet, relevance);
            foundPages.add(foundPage);
        }
        return foundPages;
    }

    public List<FoundPage> sortFoundPages() {
        List<FoundPage> pages = getFoundPages();
        List<FoundPage> sortedPages = pages.stream()
                .sorted(Comparator.comparing(FoundPage::getRelevance).reversed())
                .collect(Collectors.toList());
        return sortedPages;
    }
}
