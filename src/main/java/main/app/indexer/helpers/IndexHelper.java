package main.app.indexer.helpers;

import main.app.config.DbConnection;
import main.app.indexer.IndexPrototype;
import main.app.model.Index;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IndexHelper {

    private final List<Index> indices;
    private final List<IndexPrototype> prototypes;


    private StringBuilder builder = new StringBuilder();
    private final static int BUILDER_SIZE = 2_980_000;

    private static final Logger LOGGER = Logger.getLogger(IndexHelper.class);

    public IndexHelper() {
        this.indices = new ArrayList<>();
        this.prototypes = new ArrayList<>();
    }

    public void addIndexPrototype(IndexPrototype ip) {
        prototypes.add(ip);
    }

    public void saveIndicesToDb(Map<String, Integer> lemma2ID) throws SQLException {
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

    public void doWrite(List<Index> indices) throws SQLException {
        LOGGER.info("Starting saving indices");
        List<Index> indices2save = new ArrayList<>(indices);
        indices.clear();
                    for (int i = 0; i < indices2save.size(); i++) {
                Index index = indices2save.get(i);
                builder.append("('" + index.getLemmaId() + "', '" + index.getPageId() + "', '" + index.getRank() + "'), ");
                if (builder.length() >= BUILDER_SIZE || i == indices2save.size() - 1) {
                    DbConnection.executeMultiInsert(builder.deleteCharAt(builder.lastIndexOf(",")));
                    builder = new StringBuilder();
                }
            }
        LOGGER.info("Saved " + indices2save.size() + " indices");
        indices2save.clear();
    }
}
