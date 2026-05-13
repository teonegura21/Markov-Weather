package com.sgbd.model;

import java.time.LocalDate;

public class Anomaly {
    private String oras;
    private String tara;
    private LocalDate data;
    private boolean anomalieTemperatura;
    private boolean anomalieVant;
    private boolean anomalieUmiditate;
    private boolean anomalieUV;
    private double tempMin;
    private double tempMax;
    private double vitezaVant;
    private int umiditate;
    private int indiceUV;
    private String detaliiAnomalie;

    public String getOras() { return oras; }
    public void setOras(String oras) { this.oras = oras; }
    public String getTara() { return tara; }
    public void setTara(String tara) { this.tara = tara; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public boolean isAnomalieTemperatura() { return anomalieTemperatura; }
    public void setAnomalieTemperatura(boolean anomalieTemperatura) { this.anomalieTemperatura = anomalieTemperatura; }
    public boolean isAnomalieVant() { return anomalieVant; }
    public void setAnomalieVant(boolean anomalieVant) { this.anomalieVant = anomalieVant; }
    public boolean isAnomalieUmiditate() { return anomalieUmiditate; }
    public void setAnomalieUmiditate(boolean anomalieUmiditate) { this.anomalieUmiditate = anomalieUmiditate; }
    public boolean isAnomalieUV() { return anomalieUV; }
    public void setAnomalieUV(boolean anomalieUV) { this.anomalieUV = anomalieUV; }
    public double getTempMin() { return tempMin; }
    public void setTempMin(double tempMin) { this.tempMin = tempMin; }
    public double getTempMax() { return tempMax; }
    public void setTempMax(double tempMax) { this.tempMax = tempMax; }
    public double getVitezaVant() { return vitezaVant; }
    public void setVitezaVant(double vitezaVant) { this.vitezaVant = vitezaVant; }
    public int getUmiditate() { return umiditate; }
    public void setUmiditate(int umiditate) { this.umiditate = umiditate; }
    public int getIndiceUV() { return indiceUV; }
    public void setIndiceUV(int indiceUV) { this.indiceUV = indiceUV; }
    public String getDetaliiAnomalie() { return detaliiAnomalie; }
    public void setDetaliiAnomalie(String detaliiAnomalie) { this.detaliiAnomalie = detaliiAnomalie; }
}
