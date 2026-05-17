package com.sgbd.model;

import java.time.LocalDate;

public class HourlyForecast {
    private int cityId;
    private LocalDate forecastDate;
    private int hour;
    private double temperature;
    private int humidity;
    private double windSpeed;
    private int precipProbability;
    private int weatherCode;
    private String iconType;

    public HourlyForecast() {}

    public int getCityId() { return cityId; }
    public void setCityId(int cityId) { this.cityId = cityId; }
    public LocalDate getForecastDate() { return forecastDate; }
    public void setForecastDate(LocalDate forecastDate) { this.forecastDate = forecastDate; }
    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }
    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }
    public int getPrecipProbability() { return precipProbability; }
    public void setPrecipProbability(int precipProbability) { this.precipProbability = precipProbability; }
    public int getWeatherCode() { return weatherCode; }
    public void setWeatherCode(int weatherCode) { this.weatherCode = weatherCode; }
    public String getIconType() { return iconType; }
    public void setIconType(String iconType) { this.iconType = iconType; }
}
