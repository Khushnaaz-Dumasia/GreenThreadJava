package greenthread;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SystemTelemetryService {
    private final OperatingSystemMXBean osBean;

    public SystemTelemetryService() {
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    }

    public double getCpuUsage() {
        double load = osBean.getCpuLoad();
        if (load < 0)
            return 0; // Might be < 0 during initialization
        return load * 100.0;
    }

    public double getBrightness() {
        try {
            // Backup method via ioreg if brightness command is missing
            Process process = Runtime.getRuntime().exec(new String[] { "zsh", "-c",
                    "ioreg -r -d 1 -c AppleBacklightDisplayProjector | grep brightness | head -n 1" });
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                // Example line: "brightness" = {"min"=0,"max"=1024,"current"=512}
                if (line.contains("\"current\"=")) {
                    String currentStr = line.split("\"current\"=")[1].split("}")[0];
                    String maxStr = line.split("\"max\"=")[1].split(",")[0];
                    double current = Double.parseDouble(currentStr);
                    double max = Double.parseDouble(maxStr);
                    return (current / max) * 100.0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 50.0; // Fallback
    }
}
