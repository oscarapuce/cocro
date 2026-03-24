#!/bin/bash
# Script à exécuter UNE SEULE FOIS sur le VPS OVH
# Usage: ssh ubuntu@51.77.222.245 'bash -s' < setup-server.sh
set -e

DOMAIN="cocro.fr"
EMAIL="ton@email.com"  # change ici pour Let's Encrypt
REMOTE_DIR="/var/www/cocro"

echo "==> Mise à jour du système..."
sudo apt update && sudo apt upgrade -y

echo "==> Installation de Nginx et Certbot..."
sudo apt install -y nginx certbot python3-certbot-nginx

echo "==> Création du dossier web..."
sudo mkdir -p "$REMOTE_DIR"
sudo chown -R "$USER:$USER" "$REMOTE_DIR"

echo "==> Configuration Nginx (HTTP d'abord, avant certbot)..."
sudo tee /etc/nginx/sites-available/cocro > /dev/null <<'EOF'
server {
    listen 80;
    server_name cocro.fr www.cocro.fr;
    root /var/www/cocro;
    index index.html;
    location / {
        try_files $uri $uri/ /index.html;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/cocro /etc/nginx/sites-enabled/cocro
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx

echo "==> Génération du certificat SSL..."
sudo certbot --nginx -d "$DOMAIN" -d "www.$DOMAIN" --non-interactive --agree-tos -m "$EMAIL"

echo "==> Installation de la config Nginx finale (HTTPS + reverse proxy)..."
sudo tee /etc/nginx/sites-available/cocro > /dev/null <<'NGINXEOF'
server {
    listen 80;
    server_name cocro.fr www.cocro.fr;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name cocro.fr www.cocro.fr;

    ssl_certificate /etc/letsencrypt/live/cocro.fr/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/cocro.fr/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    location /api/ {
        proxy_pass         http://localhost:8080;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
    }

    location /ws {
        proxy_pass         http://localhost:8080;
        proxy_http_version 1.1;
        proxy_set_header   Upgrade           $http_upgrade;
        proxy_set_header   Connection        "upgrade";
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
    }

    location / {
        root  /var/www/cocro;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        root    /var/www/cocro;
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    add_header X-Frame-Options      "SAMEORIGIN";
    add_header X-Content-Type-Options "nosniff";
    add_header X-XSS-Protection     "1; mode=block";
}
NGINXEOF

sudo nginx -t
sudo systemctl reload nginx

echo "==> Renouvellement auto SSL..."
sudo systemctl enable certbot.timer

echo ""
echo "✓ Serveur prêt ! Tu peux maintenant déployer avec ./deploy.sh"
echo "✓ https://cocro.fr"
