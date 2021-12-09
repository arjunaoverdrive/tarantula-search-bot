package pagevisitor.helpers;

import model.Index;

import org.apache.log4j.Logger;
import util.DbConnection;

import java.sql.SQLException;
import java.util.*;

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

    public void convertPrototypes2Indices(Map<String, Integer> lemma2ID) {
        int i = 0;
        try {
            for (i = 0; i < prototypes.size(); i++) {
                IndexPrototype ip = prototypes.get(i);
                int lemmaId = lemma2ID.get(ip.getLemma());
                int pageId = ip.getPageId();
                float rank = ip.getRank();
                Index indx = new Index(lemmaId, pageId, rank);
                indices.add(indx);
            }
        } catch (NullPointerException npe) {
            LOGGER.error(npe + " i " + prototypes.get(i));
        }
        saveIndicesToDb(indices);
    }

    public void saveIndicesToDb(List<Index> indices) {
        List<Index> indices2save = new ArrayList<>(indices);
        indices.clear();
        try {
            for (int i = 0; i < indices2save.size(); i++) {
                Index index = indices2save.get(i);
                builder.append("('" + index.getLemmaId() + "', '" + index.getPageId() + "', '" + index.getRank() + "'), ");
                if (builder.length() >= BUILDER_SIZE || i == indices2save.size() - 1) {
                    DbConnection.executeMultiInsert(builder.deleteCharAt(builder.lastIndexOf(",")));
                    builder = new StringBuilder();
                }
            }
            indices2save.clear();
            DbConnection.closeConnection();
        } catch (SQLException e) {
            LOGGER.error(e);
        }
    }
}
