#!/bin/bash
# Script de pornire a aplicatiei Prognoza Meteo
# Seteaza variabilele de mediu pentru conectarea la PostgreSQL

cd "$(dirname "$0")"

export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5433/prognoza_meteo}"
export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-postgres}"

echo "🚀 Pornesc Prognoza Meteo — România"
echo "   DB: $DB_URL"
echo ""

if command -v xvfb-run &> /dev/null && [ -z "$DISPLAY" ]; then
    echo "   (folosesc xvfb pentru mediu headless)"
    xvfb-run -a mvn javafx:run -q
else
    mvn javafx:run -q
fi
