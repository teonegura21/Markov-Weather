package com.sgbd.model;

public class City {
    private int id;
    private String name;
    private int countryId;
    private String countryName;
    private double latitude;
    private double longitude;
    private boolean isImportant;

    public City() {}

    public City(int id, String name, int countryId, double latitude, double longitude, boolean isImportant) {
        this.id = id;
        this.name = name;
        this.countryId = countryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isImportant = isImportant;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCountryId() { return countryId; }
    public void setCountryId(int countryId) { this.countryId = countryId; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public boolean isImportant() { return isImportant; }
    public void setImportant(boolean important) { isImportant = important; }

    @Override
    public String toString() { return name + (countryName != null ? " (" + countryName + ")" : ""); }
}
