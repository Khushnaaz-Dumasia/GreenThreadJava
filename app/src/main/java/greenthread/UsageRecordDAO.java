package greenthread;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UsageRecordDAO {

    public void saveRecord(double cpu, double brightness, double co2) {
        String sql = "INSERT INTO usage_records (cpu_usage, brightness, co2_emissions) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, cpu);
            pstmt.setDouble(2, brightness);
            pstmt.setDouble(3, co2);
            pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<UsageRecord> getRecentRecords(int limit) {
        List<UsageRecord> records = new ArrayList<>();
        String sql = "SELECT timestamp, cpu_usage, brightness, co2_emissions FROM usage_records ORDER BY timestamp DESC LIMIT ?";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                records.add(new UsageRecord(
                        rs.getString("timestamp"),
                        rs.getDouble("cpu_usage"),
                        rs.getDouble("brightness"),
                        rs.getDouble("co2_emissions")));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return records;
    }

    public double getDailyTotalEmissions() {
        String sql = "SELECT SUM(co2_emissions) as daily_total FROM usage_records WHERE date(timestamp) = date('now')";
        try (Connection conn = DatabaseManager.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                // Since our records are every few seconds, we need to normalize this to actual
                // grams.
                // Our current calculateHourlyEmissions returns grams per HOUR.
                // If we save a record every 5 seconds, each record represents (5/3600) hours of
                // that emission rate.
                // Sum(co2_rate) * (5 / 3600) = Total grams.
                return rs.getDouble("daily_total") * (5.0 / 3600.0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public static class UsageRecord {
        public String timestamp;
        public double cpuUsage;
        public double brightness;
        public double co2Emissions;

        public UsageRecord(String timestamp, double cpuUsage, double brightness, double co2Emissions) {
            this.timestamp = timestamp;
            this.cpuUsage = cpuUsage;
            this.brightness = brightness;
            this.co2Emissions = co2Emissions;
        }
    }
}
