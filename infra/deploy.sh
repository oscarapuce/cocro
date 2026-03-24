#!/bin/bash
set -e

SERVER="51.77.222.245"
SERVER_USER="ubuntu"  # change si besoin (debian, root, etc.)
REMOTE_DIR="/var/www/cocro"
DIST_DIR="../cocro-angular/dist/cocro-front/browser"

echo "==> Build Angular..."
cd ../cocro-angular
npm run build -- --configuration production
cd -

echo "==> Envoi des fichiers sur le serveur..."
ssh "$SERVER_USER@$SERVER" "sudo mkdir -p $REMOTE_DIR && sudo chown -R $SERVER_USER:$SERVER_USER $REMOTE_DIR"
rsync -avz --delete "$DIST_DIR/" "$SERVER_USER@$SERVER:$REMOTE_DIR/"

echo "==> Déploiement terminé ! https://cocro.fr"
