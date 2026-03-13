package greenthread;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class DataSeeder {

    public static void main(String[] args) {
        System.out.println("Seeding database with 24 hours of historical data...");
        DatabaseManager.initializeDatabase(); // Ensure DB is ready
        seedData();
        System.out.println("Data seeding complete!");
    }

    public static void seedData() {
        String sql = "INSERT INTO usage_records (timestamp, cpu_usage, brightness, co2_emissions) VALUES (?, ?, ?, ?)";
        Random random = new Random();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Generate one record per minute for the last 24 hours (1440 records)
            for (int i = 1440; i >= 0; i--) {
                LocalDateTime recordTime = now.minusMinutes(i);
                int hour = recordTime.getHour();

                // Generate realistic patterns
                double cpu, brightness;
                
                // Night time (low usage)
                if (hour >= 23 || hour <= 6) {
                    cpu = 2.0 + random.nextDouble() * 5.0; // 2-7%
                    brightness = 0.0; // Screen likely off
                } 
                // Work hours (moderate to high usage)
                else if (hour >= 9 && hour <= 17) {
                    cpu = 15.0 + random.nextDouble() * 40.0; // 15-55%
                    brightness = 50.0 + random.nextDouble() * 30.0; // 50-80%
                } 
                // Evening (light usage / entertainment)
                else {
                    cpu = 10.0 + random.nextDouble() * 20.0; // 10-30%
                    brightness = 30.0 + random.nextDouble() * 40.0; // 30-70%
                }

                // Add occasional spikes
                if (random.nextInt(100) < 5) {
                    cpu = Math.min(cpu + 40.0, 100.0);
                }

                // Calculate CO2 based on our existing logic (roughly)
                // CPU (avg 15W max) + Screen (avg 10W max)
                double cpuWatts = (cpu / 100.0) * 15.0;
                double brightnessWatts = (brightness / 100.0) * 10.0;
                double totalWatts = 5.0 + cpuWatts + brightnessWatts; // base + cpu + bright
                // Add some random network power (0.5W to 2W)
                totalWatts += 0.5 + random.nextDouble() * 1.5; 
                double co2 = (totalWatts / 1000.0) * 450.0; // Grams per hour

                pstmt.setString(1, recordTime.format(formatter));
                pstmt.setDouble(2, cpu);
                pstmt.setDouble(3, brightness);
                pstmt.setDouble(4, co2);
                pstmt.addBatch();

                // Execute batch every 100 records
                if (i % 100 == 0) {
                    pstmt.executeBatch();
                }
            }
            pstmt.executeBatch(); // Execute remaining

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
