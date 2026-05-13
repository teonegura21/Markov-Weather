package com.sgbd.service.prediction;

import java.time.LocalDate;

/**
 * POJO pentru stocarea datelor meteo reale obtinute din API-ul Open-Meteo.
 */
public class ActualWeather {

    private LocalDate date;
    private double tempMin;
    private double tempMax;
    private double windSpeed;
    private int humidity;
    private double precipSum;
    private String iconType;

    public ActualWeather() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public double getTempMin() {
        return tempMin;
    }

    public void setTempMin(double tempMin) {
        this.tempMin = tempMin;
    }

    public double getTempMax() {
        return tempMax;
    }

    public void setTempMax(double tempMax) {
        this.tempMax = tempMax;
    }

    public double getWindSpeed() {
        return windSpeed;
    }

    public void setWindSpeed(double windSpeed) {
        this.windSpeed = windSpeed;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public double getPrecipSum() {
        return precipSum;
    }

    public void setPrecipSum(double precipSum) {
        this.precipSum = precipSum;
    }

    public String getIconType() {
        return iconType;
    }

    public void setIconType(String iconType) {
        this.iconType = iconType;
    }
}
