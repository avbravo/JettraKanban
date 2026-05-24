#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "[JettraKanban] Compilando proyecto..."
mvn -q -DskipTests compile

echo "[JettraKanban] Ejecutando aplicacion..."
mvn -q exec:java
