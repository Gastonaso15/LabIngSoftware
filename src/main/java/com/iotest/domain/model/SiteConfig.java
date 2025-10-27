package com.iotest.domain.model;

import java.util.List;

public class SiteConfig {
    private String site;
    private double maxEnergyKWh;
    private long refreshPeriodMs;
    private List<Room> rooms;

    public SiteConfig() {}

    public SiteConfig(String site, double maxEnergyKWh, long refreshPeriodMs, List<Room> rooms) {
        this.site = site;
        this.maxEnergyKWh = maxEnergyKWh;
        this.refreshPeriodMs = refreshPeriodMs;
        this.rooms = rooms;
    }

    public String getSite() { return site; }
    public void setSite(String site) { this.site = site; }

    public double getMaxEnergyKWh() { return maxEnergyKWh; }
    public void setMaxEnergyKWh(double maxEnergyKWh) { this.maxEnergyKWh = maxEnergyKWh; }

    public long getRefreshPeriodMs() { return refreshPeriodMs; }
    public void setRefreshPeriodMs(long refreshPeriodMs) { this.refreshPeriodMs = refreshPeriodMs; }

    public List<Room> getRooms() { return rooms; }
    public void setRooms(List<Room> rooms) { this.rooms = rooms; }

    // Helper: parse valores tipo "14 kWh" o "10000 ms" desde strings JSON
    public static double parseEnergy(String s) {
        if (s == null) return 0;
        return Double.parseDouble(s.replace("kWh", "").trim());
    }

    public static long parsePeriod(String s) {
        if (s == null) return 0;
        return Long.parseLong(s.replace("ms", "").trim());
    }

    @Override
    public String toString() {
        return "SiteConfig{" +
                "site='" + site + '\'' +
                ", maxEnergyKWh=" + maxEnergyKWh +
                ", refreshPeriodMs=" + refreshPeriodMs +
                ", rooms=" + rooms +
                '}';
    }
}
