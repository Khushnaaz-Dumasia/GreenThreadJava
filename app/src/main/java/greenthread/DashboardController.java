package greenthread;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import java.text.DecimalFormat;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DashboardController {

    @FXML
    private Label cpuLabel;
    @FXML
    private ProgressBar cpuProgress;
    @FXML
    private Label brightnessLabel;
    @FXML
    private ProgressBar brightnessProgress;
    @FXML
    private Label co2Label;
    @FXML
    private Label dailyTotalLabel;
    @FXML
    private ProgressBar budgetProgress;
    @FXML
    private Label budgetStatusLabel;
    @FXML
    private Label budgetTitleLabel;
    @FXML
    private Label netLabel;
    @FXML private Label netTotalLabel;
    @FXML private Label ecoTipLabel;
    @FXML private Label insightsLabel;
    @FXML private Label equivalentLabel;
    @FXML private ComboBox<String> sessionCombo;
    @FXML private Button sessionBtn;
    @FXML private Label sessionInfoLabel;
    @FXML private Label gridStatusLabel;
    
    @FXML
    private HBox greenBreakPanel;

    // Session Tracking State
    private boolean isSessionActive = false;
    private long sessionStartTime = 0;
    private double sessionStartCo2 = 0.0;
    @FXML
    private HBox ecoTipPanel;
    @FXML
    private Button realTimeBtn;
    @FXML
    private Button historyBtn;
    @FXML
    private VBox offendersBox;
    @FXML
    private AreaChart<String, Number> emissionsChart;
    @FXML
    private NumberAxis stickyYAxis;
    @FXML
    private NumberAxis internalYAxis;

    private final SystemTelemetryService telemetryService = new SystemTelemetryService();
    private final EmissionsCalculator calculator = new EmissionsCalculator();
    private final UsageRecordDAO dao = new UsageRecordDAO();
    private final XYChart.Series<String, Number> realTimeSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> historySeries = new XYChart.Series<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private long lastBytesTotal = 0;
    private double totalNetworkMB = 0;
    private boolean isHistoryMode = false;
    private static final double DAILY_BUDGET_G = 50.0;

    private final String[] ecoTips = {
            "Lowering screen brightness can significantly reduce energy consumption.",
            "Closing unused background apps saves CPU and reduces your carbon footprint.",
            "Dark Mode on OLED screens saves a surprising amount of energy.",
            "High network activity (streaming 4K) increases carbon impact significantly.",
            "Sleep mode is better than a screensaver for short breaks."
    };
    private int currentTipIndex = 0;

    @FXML
    public void initialize() {
        realTimeSeries.setName("Real-time Emissions");
        historySeries.setName("Hourly Trend");
        emissionsChart.getData().add(realTimeSeries);

        // Initialize Session Options
        sessionCombo.getItems().addAll("Coding", "Gaming", "Movie Streaming", "Web Browsing", "Designing", "Other");
        sessionCombo.getSelectionModel().selectFirst();

        fetchGridIntensity(); // Fetch immediately once
        
        // Synchronize Sticky Y-Axis bounds with Chart's active Y-Axis
        stickyYAxis.setAutoRanging(false);
        stickyYAxis.lowerBoundProperty().bind(internalYAxis.lowerBoundProperty());
        stickyYAxis.upperBoundProperty().bind(internalYAxis.upperBoundProperty());
        stickyYAxis.tickUnitProperty().bind(internalYAxis.tickUnitProperty());
        
        // Fix duplicate labels: Use a more precise formatter for the Y-Axis
        stickyYAxis.setTickLabelFormatter(new StringConverter<Number>() {
            private final DecimalFormat df = new DecimalFormat("0.###");
            @Override public String toString(Number object) { return df.format(object); }
            @Override public Number fromString(String string) { return 0; }
        });

        scheduler.scheduleAtFixedRate(this::updateStats, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::fetchGridIntensity, 30, 30, TimeUnit.MINUTES);
    }

    @FXML
    public void showRealTime() {
        isHistoryMode = false;
        emissionsChart.setTitle("Emission History (g/h)");
        emissionsChart.getData().clear();
        emissionsChart.getData().add(realTimeSeries);
        
        realTimeBtn.getStyleClass().removeAll("toggle-button", "toggle-button-active");
        historyBtn.getStyleClass().removeAll("toggle-button", "toggle-button-active");
        realTimeBtn.getStyleClass().add("toggle-button-active");
        historyBtn.getStyleClass().add("toggle-button");
        
        insightsLabel.setText("Monitoring live system metrics...");
    }

    @FXML
    public void showHistory() {
        isHistoryMode = true;
        emissionsChart.setTitle("Last 24 Hours (30min Trend)");
        emissionsChart.getData().clear();
        historySeries.getData().clear();
        
        realTimeBtn.getStyleClass().removeAll("toggle-button", "toggle-button-active");
        historyBtn.getStyleClass().removeAll("toggle-button", "toggle-button-active");
        realTimeBtn.getStyleClass().add("toggle-button");
        historyBtn.getStyleClass().add("toggle-button-active");

        List<UsageRecordDAO.UsageRecord> records = dao.getRecordsForLast24Hours();
        
        java.util.Map<String, Double> recordMap = new java.util.HashMap<>();
        for (UsageRecordDAO.UsageRecord record : records) {
            String time = record.timestamp.substring(11, 16);
            recordMap.put(time, record.co2Emissions);
        }

        LocalDateTime now = LocalDateTime.now();
        int min = now.getMinute() >= 30 ? 30 : 0;
        LocalDateTime bucketTime = now.minusHours(24).withMinute(min).withSecond(0).withNano(0);

        for (int i = 0; i <= 48; i++) {
            String timeStr = bucketTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            double co2 = recordMap.getOrDefault(timeStr, 0.0);
            historySeries.getData().add(new XYChart.Data<>(timeStr, co2));
            bucketTime = bucketTime.plusMinutes(30);
            if (bucketTime.isAfter(now)) break;
        }

        emissionsChart.getData().add(historySeries);
        generateInsights(records);
    }

    private void generateInsights(List<UsageRecordDAO.UsageRecord> records) {
        if (records.isEmpty()) {
            insightsLabel.setText("No historical data available for analysis.");
            return;
        }

        double maxCo2 = 0;
        String peakTime = "";
        double totalCo2 = 0;
        double avgCpu = 0;

        for (UsageRecordDAO.UsageRecord r : records) {
            if (r.co2Emissions > maxCo2) {
                maxCo2 = r.co2Emissions;
                peakTime = r.timestamp.substring(11, 16);
            }
            totalCo2 += r.co2Emissions;
            avgCpu += r.cpuUsage;
        }

        double avgTotal = totalCo2 / records.size();
        avgCpu /= records.size();

        String mood = avgTotal > 10 ? "heavy" : avgTotal > 5 ? "moderate" : "efficient";
        
        String highlight = String.format("Peak emissions detected at %s (%.1f g/h). ", peakTime, maxCo2);
        String summary = String.format("Overall, your usage pattern is %s with an average CPU load of %.1f%%. ", mood, avgCpu);
        String advice = avgTotal > 7 ? "Consider closing background processes during peak hours." : "Your eco-efficiency is currently excellent.";

        insightsLabel.setText(highlight + summary + advice);
    }

    private void updateStats() {
        double cpu = telemetryService.getCpuUsage();
        double brightness = telemetryService.getBrightness();

        // Network calculation
        SystemTelemetryService.NetworkData net = telemetryService.getNetworkUsage();
        long currentBytes = net.bytesIn() + net.bytesOut();
        final double speedKbps = (lastBytesTotal > 0) ? (currentBytes - lastBytesTotal) / 1024.0 / 5.0 : 0;
        lastBytesTotal = currentBytes;
        // Approximation for total MB since launch
        totalNetworkMB += (speedKbps * 5.0) / 1024.0;

        double co2 = calculator.calculateHourlyEmissions(cpu, brightness, speedKbps / 1000.0);
        double dailyTotal = dao.getDailyTotalEmissions();
        double displayTotal = isHistoryMode ? dao.get24HourTotalEmissions() : dailyTotal;

        List<SystemTelemetryService.ProcessInfo> topProcesses = telemetryService.getTopProcesses();

        Platform.runLater(() -> {
            cpuLabel.setText(String.format("%.1f%%", cpu));
            cpuProgress.setProgress(cpu / 100.0);

            brightnessLabel.setText(String.format("%.1f%%", brightness));
            brightnessProgress.setProgress(brightness / 100.0);

            netLabel.setText(String.format("%.1f KB/s", speedKbps));
            netTotalLabel.setText(String.format("Session: %.1f MB", totalNetworkMB));

            co2Label.setText(String.format("%.2f g/h", co2));
            dailyTotalLabel.setText(String.format("%.2f g", displayTotal));

            if (budgetTitleLabel != null) {
                budgetTitleLabel.setText(isHistoryMode ? "24H TOTAL" : "DAILY TOTAL");
            }

            double budgetProgressVal = displayTotal / DAILY_BUDGET_G;
            budgetProgress.setProgress(Math.min(budgetProgressVal, 1.0));
            if (budgetProgressVal > 0.9) {
                budgetStatusLabel.setText("Near Limit!");
                budgetStatusLabel.setStyle("-fx-text-fill: #f43f5e;");
            } else {
                budgetStatusLabel.setText("Eco Safe");
                budgetStatusLabel.setStyle("-fx-text-fill: #4ade80;");
            }

            double metersDriven = displayTotal / 0.120; // 120g per km = 0.120g per meter
            double phoneCharges = displayTotal / 8.0; // Assume 8g CO2 per full smartphone charge
            equivalentLabel.setText(String.format("🚗 %.0f meters driven\n📱 %.2f charges", metersDriven, phoneCharges));

            if (isSessionActive) {
                long durationMillis = System.currentTimeMillis() - sessionStartTime;
                long mins = Math.max(1, durationMillis / 60000); // show at least 1 min to prevent zero division perception
                double currentCost = dailyTotal - sessionStartCo2; // Always use strictly daily ongoing total for session costing
                sessionInfoLabel.setText(String.format("Active: %d mins | Cost: %.2fg CO2", mins, currentCost));
            }

            if (true) { // Always update real-time series in background
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                realTimeSeries.getData().add(new XYChart.Data<>(time, co2));
                if (realTimeSeries.getData().size() > 20)
                    realTimeSeries.getData().remove(0);
            }

            // Update Top Offenders
            offendersBox.getChildren().clear();
            for (SystemTelemetryService.ProcessInfo proc : topProcesses) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                Label name = new Label(proc.name());
                name.getStyleClass().add("offender-item");
                name.setMinWidth(120);
                Label val = new Label(proc.cpu());
                val.getStyleClass().add("offender-item");
                val.setStyle("-fx-font-weight: bold; -fx-text-fill: #38bdf8;");
                row.getChildren().addAll(name, val);
                offendersBox.getChildren().add(row);
            }

            boolean isHigh = co2 > 6.5;
            greenBreakPanel.setVisible(isHigh);
            ecoTipPanel.setVisible(!isHigh);

            if (LocalTime.now().getSecond() % 30 < 5) {
                currentTipIndex = (currentTipIndex + 1) % ecoTips.length;
                ecoTipLabel.setText(ecoTips[currentTipIndex]);
            }

            if (LocalTime.now().getSecond() < 5) {
                dao.saveRecord(cpu, brightness, co2);
            }
        });
    }

    @FXML
    public void triggerEcoMode() {
        try {
            // MacOS AppleScript to simulate 'Brightness Down' keystroke Multiple times
            String script = "tell application \"System Events\" to repeat 5 times\n key code 145\n end repeat";
            String[] cmd = {"osascript", "-e", script};
            Runtime.getRuntime().exec(cmd);
            
            insightsLabel.setText("Eco-Mode Activated: Screen brightness dimmed to save energy.");
        } catch (Exception e) {
            insightsLabel.setText("Error: Eco-Mode automation failed. Check permissions.");
            e.printStackTrace();
        }
    }

    @FXML
    public void toggleSession() {
        if (!isSessionActive) {
            isSessionActive = true;
            sessionStartTime = System.currentTimeMillis();
            sessionStartCo2 = dao.getDailyTotalEmissions();
            
            sessionBtn.setText("Stop Session");
            sessionBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white;");
            sessionCombo.setDisable(true);
            sessionInfoLabel.setText("Session started...");
        } else {
            isSessionActive = false;
            long durationMillis = System.currentTimeMillis() - sessionStartTime;
            double co2Cost = dao.getDailyTotalEmissions() - sessionStartCo2;
            
            long mins = durationMillis / 60000;
            String activity = sessionCombo.getValue();
            
            String report = String.format("Your %s session (%d mins) cost %.2f g of CO2.", activity, mins, co2Cost);
            sessionInfoLabel.setText(report);
            
            sessionBtn.setText("Start Session");
            sessionBtn.setStyle(""); // reset to default
            sessionCombo.setDisable(false);
        }
    }

    private void fetchGridIntensity() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.carbonintensity.org.uk/intensity"))
                    .GET()
                    .build();
            
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenAccept(this::parseGridResponse)
                    .exceptionally(e -> {
                        Platform.runLater(() -> gridStatusLabel.setText("Grid: Unavailable"));
                        return null;
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseGridResponse(String response) {
        String index = "moderate"; // Default to moderate for demo reliability
        String lowerResponse = response.toLowerCase();
        
        if (lowerResponse.contains("very low")) index = "very low";
        else if (lowerResponse.contains("low")) index = "low";
        else if (lowerResponse.contains("high")) index = "high";
        else if (lowerResponse.contains("very high")) index = "very high";
        else if (lowerResponse.contains("moderate")) index = "moderate";
        
        final String finalIndex = index;
        Platform.runLater(() -> {
            gridStatusLabel.setText("Grid: " + finalIndex.toUpperCase());
            if (finalIndex.contains("high")) {
                gridStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: white; -fx-background-color: #ef4444; -fx-padding: 3 8; -fx-background-radius: 4;");
            } else if (finalIndex.contains("low")) {
                gridStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: white; -fx-background-color: #10b981; -fx-padding: 3 8; -fx-background-radius: 4;");
            } else {
                gridStatusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: white; -fx-background-color: #f59e0b; -fx-padding: 3 8; -fx-background-radius: 4;");
            }
        });
    }

    public void stop() {
        scheduler.shutdown();
    }
}
