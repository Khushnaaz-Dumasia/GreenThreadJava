package greenthread;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DashboardController {

    @FXML
    private Label cpuLabel;
    @FXML
    private Label brightnessLabel;
    @FXML
    private Label co2Label;
    @FXML
    private VBox greenBreakPanel;
    @FXML
    private LineChart<String, Number> emissionsChart;

    private final SystemTelemetryService telemetryService = new SystemTelemetryService();
    private final EmissionsCalculator calculator = new EmissionsCalculator();
    private final UsageRecordDAO dao = new UsageRecordDAO();
    private final XYChart.Series<String, Number> series = new XYChart.Series<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @FXML
    public void initialize() {
        series.setName("Hourly Emissions");
        emissionsChart.getData().add(series);

        // Start background monitoring task
        scheduler.scheduleAtFixedRate(this::updateStats, 0, 5, TimeUnit.SECONDS);
    }

    private void updateStats() {
        double cpu = telemetryService.getCpuUsage();
        double brightness = telemetryService.getBrightness();
        double co2 = calculator.calculateHourlyEmissions(cpu, brightness);

        Platform.runLater(() -> {
            cpuLabel.setText(String.format("%.1f%%", cpu));
            brightnessLabel.setText(String.format("%.1f%%", brightness));
            co2Label.setText(String.format("%.2f g/h", co2));

            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            series.getData().add(new XYChart.Data<>(time, co2));

            // Keep only the last 20 points
            if (series.getData().size() > 20) {
                series.getData().remove(0);
            }

            // Green Break logic: Show if CO2 is high (> 6.5 g/h for example)
            greenBreakPanel.setVisible(co2 > 6.5);

            // Periodically save to DB (every 1 minute roughly, since this is every 5s)
            // In a real app we'd have a separate counter, but for this demo:
            if (LocalTime.now().getSecond() < 5) {
                dao.saveRecord(cpu, brightness, co2);
            }
        });
    }

    public void stop() {
        scheduler.shutdown();
    }
}
