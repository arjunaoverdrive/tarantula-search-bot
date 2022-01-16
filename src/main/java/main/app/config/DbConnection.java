package main.app.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DbConnection {

    private static Connection connection;

    private static final String dbName = "search_engine";
    private static final String dbUser = "se_user";
    private static final String dbPass = "testtest1234";

    public static Connection getConnection() {
        if (connection == null) {
            try {
                connection = DriverManager.getConnection(
                        "jdbc:mysql://localhost:3306/" + dbName +
                                "?user=" + dbUser + "&password=" + dbPass + "&serverTimezone=Europe/Moscow");
                String bufferSizeQuery = "SET GLOBAL bulk_insert_buffer_size=134217728";
                connection.createStatement().execute(bufferSizeQuery);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

    public static void executeMultiInsert(StringBuilder insertQuery) throws SQLException {
        String sql = "INSERT INTO `index`(lemma_id, page_id, `rank`) " +
                "VALUES " + insertQuery;
        getConnection().createStatement().execute(sql);
    }

    public static void closeConnection() throws SQLException {
        getConnection().close();
    }
}
