#!/bin/sh
# 将 Let's Encrypt PEM 证书转为 Spring Boot 可用的 PKCS12 密钥库
# 用法: ./generate-keystore.sh

set -e

CERT_DIR="/etc/letsencrypt/live/schoolforum.sugu6.top"
KEYSTORE_DIR="/opt/schoolforum/ssl"
KEYSTORE_FILE="$KEYSTORE_DIR/keystore.p12"
KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-schoolforum2026}"

if [ ! -f "$CERT_DIR/fullchain.pem" ] || [ ! -f "$CERT_DIR/privkey.pem" ]; then
    echo "证书文件不存在，请先运行 certbot 获取证书"
    exit 1
fi

mkdir -p "$KEYSTORE_DIR"

openssl pkcs12 -export \
    -in "$CERT_DIR/fullchain.pem" \
    -inkey "$CERT_DIR/privkey.pem" \
    -out "$KEYSTORE_FILE" \
    -name "schoolforum" \
    -password pass:"$KEYSTORE_PASSWORD"

chmod 644 "$KEYSTORE_FILE"
echo "密钥库已生成: $KEYSTORE_FILE"
