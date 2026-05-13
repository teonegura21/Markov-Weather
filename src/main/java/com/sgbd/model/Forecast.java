package com.sgbd.model;

import java.time.LocalDate;

public class Forecast {
    private int id;
    private int cityId;
    private String cityName;
    private String countryName;
    private LocalDate date;
    private double tempMin;
    private double tempMax;
    private double windSpeed;
    private String iconType;
    private int uvIndex;
    private int humidity;
    private String warningText;
    private long voteCount;
    private long accurateVotes;
    private double accuracyPercent;
    private long commentCount;

    public Forecast() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getCityId() { return cityId; }
    public void setCityId(int cityId) { this.cityId = cityId; }
    public String getCityName() { return cityName; }
    public void setCityName(String cityName) { this.cityName = cityName; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public double getTempMin() { return tempMin; }
    public void setTempMin(double tempMin) { this.tempMin = tempMin; }
    public double getTempMax() { return tempMax; }
    public void setTempMax(double tempMax) { this.tempMax = tempMax; }
    public double getTempAvg() { return (tempMin + tempMax) / 2.0; }
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public String getIconType() { return iconType; }
    public void setIconType(String iconType) { this.iconType = iconType; }
    public int getUvIndex() { return uvIndex; }
    public void setUvIndex(int uvIndex) { this.uvIndex = uvIndex; }
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public String getWarningText() { return warningText; }
    public void setWarningText(String warningText) { this.warningText = warningText; }
    public long getVoteCount() { return voteCount; }
    public void setVoteCount(long voteCount) { this.voteCount = voteCount; }
    public long getAccurateVotes() { return accurateVotes; }
    public void setAccurateVotes(long accurateVotes) { this.accurateVotes = accurateVotes; }
    public double getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(double accuracyPercent) { this.accuracyPercent = accuracyPercent; }
    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
}
