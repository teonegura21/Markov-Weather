#!/bin/bash
# Script de restore pentru baza de date PostgreSQL
# Restaurează un dump SQL în baza prognoza_meteo

if [ $# -ne 1 ]; then
    echo "Utilizare: $0 <cale_catre_fisier_backup>"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

BACKUP_FILE="$1"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Eroare: Fișierul '$BACKUP_FILE' nu există."
    exit 1
fi

echo "⚠️  ATENȚIE: Această operațiune va suprascrie baza de date curentă!"
echo "   Apasă Ctrl+C în 5 secunde pentru anulare..."
sleep 5

echo "⏳ Se restaurează baza de date din: $BACKUP_FILE"
docker-compose exec -T postgres psql -U postgres -d prognoza_meteo < "$BACKUP_FILE"

echo "✅ Restore realizat cu succes din: $BACKUP_FILE"
