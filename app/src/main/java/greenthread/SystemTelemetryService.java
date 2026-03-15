package greenthread;

import java.lang.management.ManagementFactory;
import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

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
        // More robust command for Apple Silicon / modern MacOS displays
        String command = "ioreg -c AppleBacklightDisplay -r -d 1 | grep -i '\"brightness\" ='";
        
        return executeCommandAndParse(command, out -> {
            try {
                // Example output: "brightness"={"min"=0,"max"=65536,"value"=32768}
                if (out.contains("\"value\"=") && out.contains("\"max\"=")) {
                    String valueStr = out.split("\"value\"=")[1].split("}")[0].split(",")[0].trim();
                    String maxStr = out.split("\"max\"=")[1].split(",")[0].split("}")[0].trim();
                    
                    double current = Double.parseDouble(valueStr);
                    double max = Double.parseDouble(maxStr);
                    
                    if (max > 0) {
                        return (current / max) * 100.0;
                    }
                }
                // Fallback for older formats (0.0 to 1.0 range)
                double val = Double.parseDouble(out.replaceAll("[^0-9.]", ""));
                return val > 1.0 ? (val / 10.24) : val * 100.0; 
            } catch (Exception e) {
                return 50.0;
            }
        }, 50.0);
    }

    public NetworkData getNetworkUsage() {
        // Using nettop to get total bytes in/out snapshots
        String output = executeCommand("nettop -P -L 1 -m route");
        long bytesIn = 0;
        long bytesOut = 0;

        String[] lines = output.split("\n");
        for (String line : lines) {
            String[] parts = line.split(",");
            if (parts.length > 5 && (line.contains("en0") || line.contains("en1"))) {
                try {
                    bytesIn += Long.parseLong(parts[2]);
                    bytesOut += Long.parseLong(parts[3]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return new NetworkData(bytesIn, bytesOut);
    }

    public List<ProcessInfo> getTopProcesses() {
        List<ProcessInfo> processes = new ArrayList<>();
        // Get top 3 CPU consuming processes
        String output = executeCommand("ps -Ao comm,%cpu -r | head -n 4");
        String[] lines = output.split("\n");
        for (int i = 1; i < lines.length; i++) { // Skip header
            String line = lines[i].trim();
            if (line.isEmpty())
                continue;
            int lastSpace = line.lastIndexOf(" ");
            if (lastSpace != -1) {
                String name = line.substring(0, lastSpace);
                String cpuStr = line.substring(lastSpace + 1);
                try {
                    double rawCpu = Double.parseDouble(cpuStr);
                    int cores = Runtime.getRuntime().availableProcessors();
                    double normalizedCpu = rawCpu / cores;
                    processes.add(new ProcessInfo(name, String.format("%.1f%%", normalizedCpu)));
                } catch (NumberFormatException e) {
                    processes.add(new ProcessInfo(name, cpuStr + "%"));
                }
            }
        }
        return processes;
    }

    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            Process p = Runtime.getRuntime().exec(new String[] { "zsh", "-c", command });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString();
    }

    private double executeCommandAndParse(String command, java.util.function.Function<String, Double> parser,
            double defaultValue) {
        String out = executeCommand(command).trim();
        if (out.isEmpty())
            return defaultValue;
        return parser.apply(out);
    }

    public static record NetworkData(long bytesIn, long bytesOut) {
    }

    public static record ProcessInfo(String name, String cpu) {
    }
}
