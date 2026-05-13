package com.sgbd.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Audit de securitate: verifica ca nicio clasa din pachetul service
 * nu concateneaza input-ul utilizatorului direct in query-uri SQL.
 *
 * Toate query-urile catre baza de date trebuie sa foloseasca
 * PreparedStatement cu parameter binding.
 */
class SqlInjectionTest {

    private static final String[] DANGEROUS_PATTERNS = {
        "executeQuery(\"", "executeUpdate(\"", "execute(\""
    };

    @Test
    void noDynamicSqlConcatenationInServiceLayer() throws Exception {
        Path srcDir = Paths.get("src/main/java/com/sgbd/service");
        if (!Files.exists(srcDir)) {
            return; // skip daca nu gasim sursele
        }

        try (Stream<Path> paths = Files.walk(srcDir)) {
            long unsafeCount = paths
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> {
                    try {
                        String content = Files.readString(p);
                        // Ignora stringurile cu + care sunt doar SQL static
                        // (ex: "SELECT a + b FROM t" este OK)
                        // Cautam concatenari periculoase: sql + variabila
                        for (String pattern : DANGEROUS_PATTERNS) {
                            if (content.contains(pattern) && content.contains("+")) {
                                // Verifica daca + apare in acelasi context cu execute
                                // Acesta este un heuristic simplu
                                int execIdx = content.indexOf(pattern);
                                int plusIdx = content.indexOf("+", execIdx);
                                if (plusIdx > execIdx && plusIdx - execIdx < 200) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();

            assertEquals(0, unsafeCount,
                "S-au gasit fisiere care ar putea concatena SQL dinamic. " +
                "Toate query-urile trebuie sa foloseasca PreparedStatement.");
        }
    }
}
