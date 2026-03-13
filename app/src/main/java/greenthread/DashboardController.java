package greenthread;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Pos;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

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
    private Label netLabel;
    @FXML
    private Label netTotalLabel;
    @FXML
    private Label ecoTipLabel;
    @FXML
    private HBox greenBreakPanel;
    @FXML
    private HBox ecoTipPanel;
    @FXML
    private VBox offendersBox;
    @FXML
    private LineChart<String, Number> emissionsChart;

    private final SystemTelemetryService telemetryService = new SystemTelemetryService();
    private final EmissionsCalculator calculator = new EmissionsCalculator();
    private final UsageRecordDAO dao = new UsageRecordDAO();
    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
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
        series.setName("Hourly Emissions");
        emissionsChart.getData().add(series);
        scheduler.scheduleAtFixedRate(this::updateStats, 0, 5, TimeUnit.SECONDS);
    }

    @FXML
    public void showRealTime() {
        isHistoryMode = false;
        emissionsChart.setTitle("Emission History (g/h)");
        series.getData().clear();
    }

    @FXML
    public void showHistory() {
        isHistoryMode = true;
        emissionsChart.setTitle("Last 24 Hours (Averaged)");
        series.getData().clear();
        List<UsageRecordDAO.UsageRecord> records = dao.getRecordsForLast24Hours();
        for (UsageRecordDAO.UsageRecord record : records) {
            String time = record.timestamp.substring(11, 16);
            series.getData().add(new XYChart.Data<>(time, record.co2Emissions));
        }
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

        List<SystemTelemetryService.ProcessInfo> topProcesses = telemetryService.getTopProcesses();

        Platform.runLater(() -> {
            cpuLabel.setText(String.format("%.1f%%", cpu));
            cpuProgress.setProgress(cpu / 100.0);

            brightnessLabel.setText(String.format("%.1f%%", brightness));
            brightnessProgress.setProgress(brightness / 100.0);

            netLabel.setText(String.format("%.1f KB/s", speedKbps));
            netTotalLabel.setText(String.format("Session: %.1f MB", totalNetworkMB));

            co2Label.setText(String.format("%.2f g/h", co2));
            dailyTotalLabel.setText(String.format("%.2f g", dailyTotal));

            double budgetProgressVal = dailyTotal / DAILY_BUDGET_G;
            budgetProgress.setProgress(Math.min(budgetProgressVal, 1.0));
            if (budgetProgressVal > 0.9) {
                budgetStatusLabel.setText("Near Limit!");
                budgetStatusLabel.setStyle("-fx-text-fill: #f43f5e;");
            } else {
                budgetStatusLabel.setText("Eco Safe");
                budgetStatusLabel.setStyle("-fx-text-fill: #4ade80;");
            }

            if (!isHistoryMode) {
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                series.getData().add(new XYChart.Data<>(time, co2));
                if (series.getData().size() > 20)
                    series.getData().remove(0);
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

    public void stop() {
        scheduler.shutdown();
    }
}
