package com.sgbd.service.prediction;

import java.time.LocalDate;

/**
 * POJO pentru stocarea metricilor de acuratete intre o predictie Monte Carlo
 * si datele meteo reale pentru o anumita data si orizont.
 */
public class AccuracyMetrics {

    private LocalDate date;
    private int horizonDay;

    private double maeTempMax;
    private double maeTempMin;
    private double rmseTempMax;
    private double biasTempMax;
    private double windError;
    private double humidityError;

    private double predictedTempMaxP50;
    private double predictedTempMinP50;
    private double predictedWindSpeedP50;
    private double predictedHumidityP50;

    private double actualTempMax;
    private double actualTempMin;
    private double actualWindSpeed;
    private double actualHumidity;

    private boolean stormHit;
    private boolean fogHit;
    private boolean heatwaveHit;
    private boolean precipHit;

    public AccuracyMetrics() {
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getHorizonDay() {
        return horizonDay;
    }

    public void setHorizonDay(int horizonDay) {
        this.horizonDay = horizonDay;
    }

    public double getMaeTempMax() {
        return maeTempMax;
    }

    public void setMaeTempMax(double maeTempMax) {
        this.maeTempMax = maeTempMax;
    }

    public double getMaeTempMin() {
        return maeTempMin;
    }

    public void setMaeTempMin(double maeTempMin) {
        this.maeTempMin = maeTempMin;
    }

    public double getRmseTempMax() {
        return rmseTempMax;
    }

    public void setRmseTempMax(double rmseTempMax) {
        this.rmseTempMax = rmseTempMax;
    }

    public double getBiasTempMax() {
        return biasTempMax;
    }

    public void setBiasTempMax(double biasTempMax) {
        this.biasTempMax = biasTempMax;
    }

    public double getWindError() {
        return windError;
    }

    public void setWindError(double windError) {
        this.windError = windError;
    }

    public double getHumidityError() {
        return humidityError;
    }

    public void setHumidityError(double humidityError) {
        this.humidityError = humidityError;
    }

    public double getPredictedTempMaxP50() {
        return predictedTempMaxP50;
    }

    public void setPredictedTempMaxP50(double predictedTempMaxP50) {
        this.predictedTempMaxP50 = predictedTempMaxP50;
    }

    public double getPredictedTempMinP50() {
        return predictedTempMinP50;
    }

    public void setPredictedTempMinP50(double predictedTempMinP50) {
        this.predictedTempMinP50 = predictedTempMinP50;
    }

    public double getPredictedWindSpeedP50() {
        return predictedWindSpeedP50;
    }

    public void setPredictedWindSpeedP50(double predictedWindSpeedP50) {
        this.predictedWindSpeedP50 = predictedWindSpeedP50;
    }

    public double getPredictedHumidityP50() {
        return predictedHumidityP50;
    }

    public void setPredictedHumidityP50(double predictedHumidityP50) {
        this.predictedHumidityP50 = predictedHumidityP50;
    }

    public double getActualTempMax() {
        return actualTempMax;
    }

    public void setActualTempMax(double actualTempMax) {
        this.actualTempMax = actualTempMax;
    }

    public double getActualTempMin() {
        return actualTempMin;
    }

    public void setActualTempMin(double actualTempMin) {
        this.actualTempMin = actualTempMin;
    }

    public double getActualWindSpeed() {
        return actualWindSpeed;
    }

    public void setActualWindSpeed(double actualWindSpeed) {
        this.actualWindSpeed = actualWindSpeed;
    }

    public double getActualHumidity() {
        return actualHumidity;
    }

    public void setActualHumidity(double actualHumidity) {
        this.actualHumidity = actualHumidity;
    }

    public boolean isStormHit() {
        return stormHit;
    }

    public void setStormHit(boolean stormHit) {
        this.stormHit = stormHit;
    }

    public boolean isFogHit() {
        return fogHit;
    }

    public void setFogHit(boolean fogHit) {
        this.fogHit = fogHit;
    }

    public boolean isHeatwaveHit() {
        return heatwaveHit;
    }

    public void setHeatwaveHit(boolean heatwaveHit) {
        this.heatwaveHit = heatwaveHit;
    }

    public boolean isPrecipHit() {
        return precipHit;
    }

    public void setPrecipHit(boolean precipHit) {
        this.precipHit = precipHit;
    }
}
