#!/bin/bash
# Script de pornire a aplicatiei Prognoza Meteo
# Seteaza variabilele de mediu pentru conectarea la PostgreSQL
#
# Nota: Daca exista ./mvnw (Maven Wrapper), se poate folosi in loc de `mvn`.

cd "$(dirname "$0")"

# Determina comanda Maven: prefera wrapper-ul daca exista, altfel foloseste mvn
if [ -f "./mvnw" ]; then
    MVN_CMD="./mvnw"
else
    MVN_CMD="mvn"
fi

export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5433/prognoza_meteo}"
export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "🚀 Pornesc Prognoza Meteo — România"
echo "   DB: $DB_URL"
echo ""

# Foloseste xvfb daca nu exista display si este disponibil
if command -v xvfb-run &> /dev/null && [ -z "$DISPLAY" ]; then
    echo "   (folosesc xvfb pentru mediu headless)"
    xvfb-run -a "$MVN_CMD" javafx:run -q
else
    "$MVN_CMD" javafx:run -q
fi
