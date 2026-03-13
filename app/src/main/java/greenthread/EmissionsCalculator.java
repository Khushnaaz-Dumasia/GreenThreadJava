package greenthread;

public class EmissionsCalculator {
    // Constants for energy calculation (Approximated for a typical laptop)
    private static final double BASE_POWER_WATTS = 5.0; // Idle power
    private static final double MAX_CPU_POWER_WATTS = 15.0; // Peak CPU power
    private static final double MAX_BRIGHTNESS_POWER_WATTS = 3.0; // Peak screen power

    // Average carbon intensity for electricity (gCO2eq/kWh)
    // Global average is ~475, but we can use a configurable or specific value.
    private static final double CARBON_INTENSITY = 450.0;

    /**
     * Calculates current CO2 emissions in grams per hour.
     * 
     * @param cpuUsage   Percentage (0-100)
     * @param brightness Percentage (0-100)
     * @return grams of CO2 per hour
     */
    public double calculateHourlyEmissions(double cpuUsage, double brightness, double networkMbps) {
        double cpuPower = (cpuUsage / 100.0) * MAX_CPU_POWER_WATTS;
        double brightnessPower = (brightness / 100.0) * MAX_BRIGHTNESS_POWER_WATTS;

        // Network impact: 0.05W per Mbps of data transfer (estimated)
        double networkPower = networkMbps * 0.05;

        double totalPowerWatts = BASE_POWER_WATTS + cpuPower + brightnessPower + networkPower;

        // Convert Watts to kWh for one hour: (Watts * 1 hour) / 1000
        double energyKWh = totalPowerWatts / 1000.0;

        // Return grams of CO2
        return energyKWh * CARBON_INTENSITY;
    }
}
