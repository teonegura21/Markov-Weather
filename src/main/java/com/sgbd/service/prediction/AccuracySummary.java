package com.sgbd.service.prediction;

/**
 * POJO pentru agregarea metricilor de acuratete pe un set de comparatii.
 */
public class AccuracySummary {

    private double overallMae;
    private double overallRmse;
    private double overallBias;
    private int totalComparisons;
    private double eventHitRate;

    public AccuracySummary() {
    }

    public AccuracySummary(double overallMae, double overallRmse, double overallBias,
                           int totalComparisons, double eventHitRate) {
        this.overallMae = overallMae;
        this.overallRmse = overallRmse;
        this.overallBias = overallBias;
        this.totalComparisons = totalComparisons;
        this.eventHitRate = eventHitRate;
    }

    public double getOverallMae() {
        return overallMae;
    }

    public void setOverallMae(double overallMae) {
        this.overallMae = overallMae;
    }

    public double getOverallRmse() {
        return overallRmse;
    }

    public void setOverallRmse(double overallRmse) {
        this.overallRmse = overallRmse;
    }

    public double getOverallBias() {
        return overallBias;
    }

    public void setOverallBias(double overallBias) {
        this.overallBias = overallBias;
    }

    public int getTotalComparisons() {
        return totalComparisons;
    }

    public void setTotalComparisons(int totalComparisons) {
        this.totalComparisons = totalComparisons;
    }

    public double getEventHitRate() {
        return eventHitRate;
    }

    public void setEventHitRate(double eventHitRate) {
        this.eventHitRate = eventHitRate;
    }
}
