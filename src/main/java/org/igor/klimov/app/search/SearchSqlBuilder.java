package org.igor.klimov.app.search;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class SearchSqlBuilder {

    String createSqlToGetLemmas(Set<String> lemmas, int siteId) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT id, lemma, frequency, site_id FROM lemma WHERE lemma IN (");

        if (lemmas.size() == 0) {
            return "";
        }
        for (String s : lemmas) {
            sql.append("'").append(s).append("'").append(", ");
        }
        sql.delete(sql.lastIndexOf(","), sql.length());
        sql.append(")");
        if (siteId != -1) {
            sql.append(" AND site_id = ").append(siteId);
        }

        return sql.toString();
    }

    String createSqlToGetPageCountForSites(int siteId) {

        if (siteId == -1) {
            String s = "SELECT p.site_id, COUNT(*) as page_count FROM page p GROUP BY p.site_id";
            return s;
        } else

        return createSqlToGetPageCountForASingleSite(siteId);
    }

    String createSqlToGetPageCountForASingleSite(int siteId) {

        StringBuilder builder = new StringBuilder("SELECT site_id, COUNT(*) as page_count FROM page WHERE site_id = ");
        builder.append(siteId);
        builder.append(" GROUP BY page.site_id");
        return builder.toString();
    }

    String createSqlToGetPagesContainingLemmas(List<Integer> lemmas, int lemmasCount) {

//        int lemmasSize = lemmas.stream().map(l -> l);

        StringBuilder builder = new StringBuilder(
                "SELECT page_id FROM" +
                "(SELECT i.page_id, COUNT(*) as count FROM index i WHERE i.lemma_id IN( ");
        for (int lemmaId : lemmas) {
            builder.append(lemmaId);
            builder.append(", ");
        }

        builder.delete(builder.lastIndexOf(", "), builder.length());
        builder.append(") GROUP BY i.page_id ORDER BY count DESC) AS page2count WHERE count = " + lemmasCount);

        return builder.toString();
    }

    public String createSqlToGetPageToRelevanceMap(List<Integer> lemmaIds, List<Integer> pageIds, int limit, int offset) {

        StringBuilder builder = new StringBuilder("SELECT page_id, SUM(rank) as relevance FROM index WHERE " +
                "lemma_id IN (");
        lemmaIds.forEach(i -> builder.append(i).append(", "));
        builder.delete(builder.lastIndexOf(","), builder.length());

        builder.append(") AND page_id IN (");
        pageIds.forEach(i -> builder.append(i).append(", "));
        builder.delete(builder.lastIndexOf(","), builder.length());

        builder.append(") GROUP BY page_id ORDER BY relevance DESC LIMIT ");
        builder.append(limit);
        builder.append(" OFFSET ");
        builder.append(offset);

        return builder.toString();

    }

    public String createSqlToGetResultPages(Set<Integer> pageIds) {

        StringBuilder builder = new StringBuilder("SELECT * FROM page WHERE id IN (");

        pageIds.forEach(i -> builder.append(i).append(", "));
        builder.delete(builder.lastIndexOf(","), builder.length());
        builder.append(")");

        return builder.toString();
    }
}