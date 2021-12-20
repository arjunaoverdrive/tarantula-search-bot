package search;

import lemmatizer.LemmaCounter;
import model.Index;
import model.Lemma;
import model.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import util.DbConnection;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class SearchHelper {
    String query;
    private final LemmaCounter counter;

    public SearchHelper(String query) throws IOException {
        this.query = query;
        this.counter = new LemmaCounter();
    }

    private Set<String> getStringSet() {
        return counter.countLemmas(query).keySet();
    }

    private String createSqlToGetLemmas(){
        StringBuilder sql = new StringBuilder("SELECT id, lemma, frequency FROM lemma WHERE lemma IN (");
        for(String s : getStringSet()){
            sql.append("'").append(s).append("'").append(", ");
        }
        sql.deleteCharAt(sql.length() - 2);
        sql.append(")");
        return sql.toString();
    }

    private List<Lemma> getLemmas(){
        List<Lemma> lemmas = new ArrayList<>();
        try {
            ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(createSqlToGetLemmas());
            while(rs.next()){
                Lemma l = new Lemma();
                l.setId(rs.getInt("id"));
                l.setLemma(rs.getString("lemma"));
                l.setFrequency(rs.getInt("frequency"));
                lemmas.add(l);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
        return pageCount;
    }

    private List<Lemma> sortLemmasByFrequency() {
        float threshold = getMaxFrequency() * 0.7f;
        List<Lemma> lemmas = getLemmas();
        return lemmas
                .stream()
                .sorted(Comparator.comparing(Lemma::getFrequency))
                .filter(lemma -> lemma.getFrequency() < threshold && lemma.getFrequency() != 0)
//                .map(lemma -> lemma.getLemma())
                .collect(Collectors.toList());
    }

    public List<Lemma> getSortedList() {

        return sortLemmasByFrequency();
    }

    private List<Integer> getLemmasIds() {
        List<Lemma> lemmas = getSortedList();
        List<Integer> ids = new ArrayList<>();
        for(Lemma l : lemmas){
            ids.add(l.getId());
        }
        return ids;
    }

    private List<Integer> getPageIdsByLemmaId() {
        List<Integer> lemmaIds = getLemmasIds();
        List<Integer> pagesIds = new ArrayList<>();
        List<Integer> pages = new ArrayList<>();
        for (int i = 0; i < lemmaIds.size(); i++) {
            String sql = "SELECT page_id FROM `index` WHERE lemma_id = " + lemmaIds.get(i);
            try {
                ResultSet rs = DbConnection.getConnection().createStatement().executeQuery(sql);
                while (rs.next()) {
                    int id = rs.getInt("page_id");
                    pagesIds.add(id);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            if (i == 0) {
                pages.addAll(pagesIds);
            } else {
                pages.retainAll(pagesIds);
            }
            pagesIds.clear();
        }
        return pages;
    }

    private String createSqlToGetIndices() {
        List<Integer> lemmaIds = getLemmasIds();
        List<Integer> pageIds = getPageIdsByLemmaId();
        StringBuilder sql = new StringBuilder("SELECT lemma_id, page_id, `rank` FROM `index` WHERE lemma_id IN (");
        for (Integer lemmaId : lemmaIds) {
            sql.append(lemmaId).append(", ");
        }
        sql.deleteCharAt(sql.length() - 2);
        sql.append(") AND page_id IN (");
        for (Integer pageId : pageIds) {
            sql.append(pageId).append(", ");
        }
        sql.deleteCharAt(sql.length() - 2);
        sql.append(") LIMIT 100");
        return sql.toString();
    }

    private List<Index> getIndices() {
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
            e.printStackTrace();
        }
        return indices;
    }

    private Map<Integer, Float> countAbsoluteRelevanceForPages() {
        List<Index> indices = getIndices();
        return indices.stream()
                .collect(Collectors.toMap(Index::getPageId, Index::getRank, Float::sum, HashMap::new));
    }

    private float getMaxRelevanceFromPagesList() {
        Map<Integer, Float> page2absoluteRelevance = countAbsoluteRelevanceForPages();
        return page2absoluteRelevance.values()
                .stream()
                .max(Float::compareTo)
                .get();
    }

    private Map<Integer, Float> calculateRelevanceForPages(){
        Map<Integer, Float> page2absRelevance = countAbsoluteRelevanceForPages();
        Set<Integer> ids = page2absRelevance.keySet();
        Map<Integer, Float> page2relevance = new HashMap<>();
        float max = getMaxRelevanceFromPagesList();
        for(int id : ids){
            float absRel = page2absRelevance.get(id);
            float rel = absRel / max;
            page2relevance.put(id, rel);
        }
        return page2relevance;
    }

    private String createQuery2getPagesList(){
        Set<Integer> pagesIds = calculateRelevanceForPages().keySet();
        StringBuilder sql = new StringBuilder("SELECT id, path, content FROM page WHERE id IN (");
        for(int id : pagesIds){
            sql.append(id).append(", ");
        }
        sql.deleteCharAt(sql.length() - 2);
        sql.append(")");
        return sql.toString();
    }

    private List<Page> getPages(){
        List<Page>foundPages = new ArrayList<>();
        try {
            ResultSet rs = DbConnection.getConnection().createStatement()
                    .executeQuery(createQuery2getPagesList());
            while(rs.next()){
                Page p = new Page();
                p.setId(rs.getInt("id"));
                p.setPath(rs.getString("path"));
                p.setContent(rs.getString("content"));
                foundPages.add(p);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return foundPages;
    }

    private String getTitle(String html){
        return Jsoup.parse(html).select("title").text();
    }

    private String getQueryRegex(){
        String[]strings = query.split(" ");
        List<String> stringsList = Arrays.stream(strings)
                .filter(s -> s.length() > 2)
                .map(s -> s.replaceAll("[^А-Яа-я]", ""))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder("(?s).*");
        for(String s : stringsList){
            builder.append(s).append(".*");
        }
        return builder.toString();
    }

    private String getSnippetPagePart(String html){
        Document doc = Jsoup.parse(html);
        String queryPattern = getQueryRegex();
        List<Element> elements = doc.getAllElements();

        String res = elements.stream()
                .map(Element::ownText)
                .filter(s -> s.matches(queryPattern))
                .findFirst().get();
        return res;
    }


    private int getFirstOccurrenceIndex(String text){
        String[]strings = query.replaceAll("[^А-Яа-я]", " ").split("\\s");
        List<String> strsList = Arrays.stream(strings).filter(s -> s.length() > 3).collect(Collectors.toList());
        int offset = text.indexOf(strsList.get(0));
        for(String s : strsList){
            if(text.indexOf(s) < offset)
                offset = text.indexOf(s);
        }
        return offset;
    }

    //todo write a method to highlight occurrences of the search in the text

    private List<FoundPage> getFoundPages(){
        List<FoundPage>foundPages = new ArrayList<>();
        Map<Integer, Float> page2rel = calculateRelevanceForPages();
        List<Page>pages = getPages();
        for(Page p : pages){
            String uri = p.getPath();
            String pageText = p.getContent();
            String title = getTitle(pageText);
//            int offset = getFirstOccurrenceIndex(getSnippetPagePart(pageText));
            String snippet = getSnippetPagePart(pageText);
//                    .substring(offset);
            float relevance = page2rel.get(p.getId());
            FoundPage foundPage = new FoundPage(uri, title, snippet, relevance);
            foundPages.add(foundPage);
        }
        return foundPages;
    }

    public List<FoundPage> sortFoundPages(){
        List<FoundPage>pages = getFoundPages();
        List<FoundPage> sortedPages = pages.stream()
                .sorted(Comparator.comparing(FoundPage::getRelevance).reversed())
                .collect(Collectors.toList());
        return sortedPages;
    }
}
