package greenthread;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:greenthread.db";

    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS usage_records (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "cpu_usage REAL," +
                "brightness REAL," +
                "co2_emissions REAL" +
                ");";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
