#!/bin/bash
set -e

echo "🔍 Verificare setup Prognoza Meteo..."

# 1. Verifica Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker nu este instalat"
    exit 1
fi
echo "✅ Docker este instalat"

# 2. Verifica docker-compose.yml
if [ ! -f "docker-compose.yml" ]; then
    echo "❌ docker-compose.yml nu a fost gasit"
    exit 1
fi
echo "✅ docker-compose.yml exista"

# 3. Compileaza proiectul
echo "🔨 Se ruleaza mvn compile..."
if ! mvn compile -q; then
    echo "❌ mvn compile a esuat"
    exit 1
fi
echo "✅ mvn compile a reusit"

# 4. Ruleaza testele
echo "🧪 Se ruleaza mvn test..."
if ! mvn test -q; then
    echo "❌ mvn test a esuat"
    exit 1
fi
echo "✅ mvn test a reusit (0 failures)"

echo ""
echo "✅ Setup OK"
