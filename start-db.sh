#!/bin/bash
# Script de pornire a bazei de date PostgreSQL pentru Prognoza Meteo

echo "🐳 Pornesc containerul PostgreSQL..."
docker-compose up -d

echo "⏳ Aștept ca PostgreSQL să fie gata..."
until docker exec prognoza-meteo-db pg_isready -U postgres -d prognoza_meteo > /dev/null 2>&1; do
    sleep 1
done

echo "✅ PostgreSQL este activ!"
echo ""
echo "URL:     jdbc:postgresql://localhost:5433/prognoza_meteo"
echo "User:    postgres"
echo "Parola:  postgres"
echo ""
echo "Pentru a porni aplicația:  mvn javafx:run"
echo "Pentru a opri baza:        docker-compose down"
