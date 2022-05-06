package org.igor.klimov.app.indexer.helpers;

import org.igor.klimov.app.model.Index;
import org.igor.klimov.app.indexer.IndexPrototype;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndexHelper {

    private final List<Index> indices;
    private final List<IndexPrototype> prototypes;
    private final JdbcTemplate template;

    private StringBuilder builder = new StringBuilder();
    private final static int BUILDER_SIZE = 2_980_000;

    private static final Logger LOGGER = Logger.getLogger(IndexHelper.class);


    public IndexHelper(JdbcTemplate template) {
        this.indices = new ArrayList<>();
        this.prototypes = new ArrayList<>();
        this.template = template;
    }

    public void addIndexPrototype(IndexPrototype ip) {
        prototypes.add(ip);
    }

    public void convertPrototypes2Indices(Map<String, Integer> lemma2ID) throws SQLException {
        for (int i = 0; i < prototypes.size(); i++) {
            IndexPrototype ip = prototypes.get(i);
            int lemmaId = -1;
            try {
                lemmaId = lemma2ID.get(ip.getLemma());
            } catch (NullPointerException npe) { //for debugging purposes
                LOGGER.error(npe + " i " + i + " " + prototypes.get(i));
            }
            int pageId = ip.getPageId();
            float rank = ip.getRank();
            Index indx = new Index(lemmaId, pageId, rank);
            indices.add(indx);
        }
        doWrite(indices);
    }

    public void doWrite(List<Index> indices) {
        LOGGER.info("Starting saving indices");
        List<Index> indices2save = new ArrayList<>(indices);
        indices.clear();
        for (int i = 0; i < indices2save.size(); i++) {
            Index index = indices2save.get(i);
            builder.append("('" + index.getLemmaId() + "', '" + index.getPageId() + "', '" + index.getRank() + "'), ");
            if (builder.length() >= BUILDER_SIZE || i == indices2save.size() - 1) {
                template.execute("INSERT INTO index (lemma_id, page_id, rank) " +
                        "VALUES " + builder.deleteCharAt(builder.lastIndexOf(",")));
                builder = new StringBuilder();
            }
        }
        LOGGER.info("Saved " + indices2save.size() + " indices");
        indices2save.clear();
    }
}
