package com.sgbd.util;

import java.time.LocalDate;

/**
 * Utilitar de validare pentru input-urile utilizatorului.
 * Previnte erori de tip NumberFormatException si valori nerezonabile.
 */
public final class ValidationUtil {

    private ValidationUtil() {}

    /**
     * Valideaza ca o valoare numerica este in intervalul [min, max].
     *
     * @param value valoarea de validat
     * @param min   limita inferioara
     * @param max   limita superioara
     * @param name  numele campului (pentru mesaje de eroare)
     * @return mesaj de eroare sau null daca e valid
     */
    public static String validateRange(int value, int min, int max, String name) {
        if (value < min) {
            return name + " trebuie sa fie cel putin " + min;
        }
        if (value > max) {
            return name + " trebuie sa fie cel mult " + max;
        }
        return null;
    }

    /**
     * Valideaza ca o data nu este in viitor.
     */
    public static String validateNotFuture(LocalDate date, String name) {
        if (date.isAfter(LocalDate.now())) {
            return name + " nu poate fi in viitor";
        }
        return null;
    }

    /**
     * Valideaza ca o data este in intervalul rezonabil pentru istoric
     * (nu mai veche de 50 ani, nu in viitor).
     */
    public static String validateHistoricalDate(LocalDate date, String name) {
        LocalDate now = LocalDate.now();
        LocalDate min = now.minusYears(50);
        if (date.isAfter(now)) {
            return name + " nu poate fi in viitor";
        }
        if (date.isBefore(min)) {
            return name + " nu poate fi mai veche de 50 de ani";
        }
        return null;
    }
}
