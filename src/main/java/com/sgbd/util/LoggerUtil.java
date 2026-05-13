package com.sgbd.util;

import java.util.logging.Logger;

/**
 * Utilitar simplu pentru logging.
 * Oferă o metodă statică pentru obținerea unui logger asociat unei clase.
 */
public class LoggerUtil {

    private LoggerUtil() {
        // clasă utilitară — nu se instanțiază
    }

    /**
     * Returnează un logger pentru clasa specificată.
     *
     * @param clazz clasa pentru care se cere logger-ul
     * @return instanță de {@link Logger}
     */
    public static Logger getLogger(Class<?> clazz) {
        return Logger.getLogger(clazz.getName());
    }
}
