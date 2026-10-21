#!/usr/bin/env bash
# Steuerung der lokalen Jellyfin-Testumgebung.
set -euo pipefail
cd "$(dirname "$0")"

case "${1:-start}" in
start)
	./generate-media.sh
	docker compose up -d
	./setup-server.sh
	;;
stop)
	docker compose down
	;;
reset)
	# Server-Daten löschen (Benutzer, Bibliotheken, Metadaten), Medien bleiben erhalten
	docker compose down
	rm -rf data
	echo "Server-Daten gelöscht. Neu aufsetzen mit: ./testenv.sh start"
	;;
reset-all)
	docker compose down
	rm -rf data media
	echo "Server-Daten und Medien gelöscht. Neu aufsetzen mit: ./testenv.sh start"
	;;
logs)
	docker compose logs -f
	;;
adb)
	# Leitet localhost:8096 auf dem Gerät/Emulator an den Mac weiter
	adb reverse tcp:8096 tcp:8096
	echo "Auf dem Gerät ist der Server jetzt unter http://localhost:8096 erreichbar."
	;;
*)
	echo "Verwendung: $0 {start|stop|reset|reset-all|logs|adb}"
	exit 1
	;;
esac

