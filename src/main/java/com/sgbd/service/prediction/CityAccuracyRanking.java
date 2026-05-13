package com.sgbd.service.prediction;

/**
 * POJO pentru clasamentul orașelor dupa acuratetea predictiilor.
 */
public class CityAccuracyRanking {

    private int cityId;
    private String cityName;
    private double mae;
    private double rmse;
    private int totalComparisons;

    public CityAccuracyRanking() {
    }

    public CityAccuracyRanking(int cityId, String cityName, double mae, double rmse, int totalComparisons) {
        this.cityId = cityId;
        this.cityName = cityName;
        this.mae = mae;
        this.rmse = rmse;
        this.totalComparisons = totalComparisons;
    }

    public int getCityId() {
        return cityId;
    }

    public void setCityId(int cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public double getMae() {
        return mae;
    }

    public void setMae(double mae) {
        this.mae = mae;
    }

    public double getRmse() {
        return rmse;
    }

    public void setRmse(double rmse) {
        this.rmse = rmse;
    }

    public int getTotalComparisons() {
        return totalComparisons;
    }

    public void setTotalComparisons(int totalComparisons) {
        this.totalComparisons = totalComparisons;
    }
}
