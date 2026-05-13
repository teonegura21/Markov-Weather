#!/bin/bash
# Script de backup pentru baza de date PostgreSQL
# Creează un dump SQL al bazei prognoza_meteo

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p backups

BACKUP_FILE="backups/$(date +%Y-%m-%d)_prognoza_meteo.sql"

echo "⏳ Se creează backup-ul bazei de date..."
docker-compose exec -T postgres pg_dump -U postgres -d prognoza_meteo > "$BACKUP_FILE"

echo "✅ Backup realizat cu succes: $BACKUP_FILE"
