package com.sgbd.model;

import java.time.LocalDateTime;

public class Vote {
    private int id;
    private int userId;
    private int forecastId;
    private boolean isAccurate;
    private LocalDateTime createdAt;

    public Vote() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public int getForecastId() { return forecastId; }
    public void setForecastId(int forecastId) { this.forecastId = forecastId; }
    public boolean isAccurate() { return isAccurate; }
    public void setAccurate(boolean accurate) { isAccurate = accurate; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
