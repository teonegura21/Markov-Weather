package com.sgbd.service.prediction;

import java.time.LocalDateTime;

/**
 * POJO pentru logul procesului de reinforcement learning.
 * Stocheaza ajustarile facute asupra ponderilor Markov si emisiilor HMM.
 */
public class ReinforcementLog {

    private long id;
    private int iteration;
    private String parameterType;
    private String parameterKey;
    private double oldValue;
    private double newValue;
    private double accuracyBefore;
    private double accuracyAfter;
    private Integer cityId;
    private LocalDateTime createdAt;

    public ReinforcementLog() {
    }

    public ReinforcementLog(long id, int iteration, String parameterType, String parameterKey,
                            double oldValue, double newValue, double accuracyBefore,
                            double accuracyAfter, Integer cityId, LocalDateTime createdAt) {
        this.id = id;
        this.iteration = iteration;
        this.parameterType = parameterType;
        this.parameterKey = parameterKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.accuracyBefore = accuracyBefore;
        this.accuracyAfter = accuracyAfter;
        this.cityId = cityId;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public int getIteration() {
        return iteration;
    }

    public void setIteration(int iteration) {
        this.iteration = iteration;
    }

    public String getParameterType() {
        return parameterType;
    }

    public void setParameterType(String parameterType) {
        this.parameterType = parameterType;
    }

    public String getParameterKey() {
        return parameterKey;
    }

    public void setParameterKey(String parameterKey) {
        this.parameterKey = parameterKey;
    }

    public double getOldValue() {
        return oldValue;
    }

    public void setOldValue(double oldValue) {
        this.oldValue = oldValue;
    }

    public double getNewValue() {
        return newValue;
    }

    public void setNewValue(double newValue) {
        this.newValue = newValue;
    }

    public double getAccuracyBefore() {
        return accuracyBefore;
    }

    public void setAccuracyBefore(double accuracyBefore) {
        this.accuracyBefore = accuracyBefore;
    }

    public double getAccuracyAfter() {
        return accuracyAfter;
    }

    public void setAccuracyAfter(double accuracyAfter) {
        this.accuracyAfter = accuracyAfter;
    }

    public Integer getCityId() {
        return cityId;
    }

    public void setCityId(Integer cityId) {
        this.cityId = cityId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
