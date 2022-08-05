package org.arjunaoverdrive.app.indexer.helpers;

import org.apache.log4j.Logger;
import org.arjunaoverdrive.app.indexer.IndexPrototype;
import org.arjunaoverdrive.app.model.Index;
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
            IndexPrototype prototype = prototypes.get(i);
            int lemmaId = -1;
            try {
                lemmaId = lemma2ID.get(prototype.getLemma());
            } catch (NullPointerException npe) { //for debugging purposes
                LOGGER.error(npe + " i " + i + " " + prototypes.get(i));
            }
            int pageId = prototype.getPageId();
            float rank = prototype.getRank();
            indices.add(new Index(lemmaId, pageId, rank));
        }
        saveIndices(indices);
    }

    public void saveIndices(List<Index> indices) {
        LOGGER.info("Starting saving indices");
        for (int i = 0; i < indices.size(); i++) {
            Index index = indices.get(i);
            appendIndexDataToBuilder(index);
            if (builder.length() >= BUILDER_SIZE || i == indices.size() - 1) {
                flushIndexBuffer();
                builder = new StringBuilder();
            }
        }
        LOGGER.info("Saved " + indices.size() + " indices");
        indices.clear();
    }

    private void appendIndexDataToBuilder(Index index) {
        builder.append("('" + index.getLemmaId() + "', '" + index.getPageId() + "', '" + index.getRank() + "'), ");
    }

    private void flushIndexBuffer() {
        template.execute("INSERT INTO index (lemma_id, page_id, rank) " +
                "VALUES " + builder.deleteCharAt(builder.lastIndexOf(",")));
    }
}
