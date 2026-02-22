#!/bin/bash
# =============================================================================
# Generate self-signed CA + service certificates for mTLS demo
# =============================================================================
set -e

CERTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PASSWORD="changeit"

echo "=== Generating CA ==="
keytool -genkeypair -alias ca -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=Demo CA, O=Demo, L=London, C=GB" \
  -keystore "$CERTS_DIR/ca.p12" -storetype PKCS12 \
  -storepass "$PASSWORD" -keypass "$PASSWORD" \
  -ext BasicConstraints:critical=ca:true

# Export CA certificate
keytool -exportcert -alias ca -keystore "$CERTS_DIR/ca.p12" -storetype PKCS12 \
  -storepass "$PASSWORD" -file "$CERTS_DIR/ca.pem" -rfc

echo "=== Generating service certificates ==="
for SERVICE in api-gateway greeting-service time-service; do
  echo "--- $SERVICE ---"

  # Generate keypair
  keytool -genkeypair -alias "$SERVICE" -keyalg RSA -keysize 2048 -validity 365 \
    -dname "CN=$SERVICE, O=Demo, L=London, C=GB" \
    -keystore "$CERTS_DIR/$SERVICE.p12" -storetype PKCS12 \
    -storepass "$PASSWORD" -keypass "$PASSWORD" \
    -ext "SAN=dns:$SERVICE,dns:localhost"

  # Create CSR
  keytool -certreq -alias "$SERVICE" -keystore "$CERTS_DIR/$SERVICE.p12" -storetype PKCS12 \
    -storepass "$PASSWORD" -file "$CERTS_DIR/$SERVICE.csr"

  # Sign with CA
  keytool -gencert -alias ca -keystore "$CERTS_DIR/ca.p12" -storetype PKCS12 \
    -storepass "$PASSWORD" -infile "$CERTS_DIR/$SERVICE.csr" \
    -outfile "$CERTS_DIR/$SERVICE-signed.pem" -rfc \
    -ext "SAN=dns:$SERVICE,dns:localhost" -validity 365

  # Import CA cert into service keystore
  keytool -importcert -alias ca -keystore "$CERTS_DIR/$SERVICE.p12" -storetype PKCS12 \
    -storepass "$PASSWORD" -file "$CERTS_DIR/ca.pem" -noprompt

  # Import signed cert into service keystore
  keytool -importcert -alias "$SERVICE" -keystore "$CERTS_DIR/$SERVICE.p12" -storetype PKCS12 \
    -storepass "$PASSWORD" -file "$CERTS_DIR/$SERVICE-signed.pem" -noprompt

  # Clean up CSR and intermediate
  rm -f "$CERTS_DIR/$SERVICE.csr" "$CERTS_DIR/$SERVICE-signed.pem"
done

echo "=== Creating shared truststore ==="
keytool -importcert -alias ca -keystore "$CERTS_DIR/truststore.p12" -storetype PKCS12 \
  -storepass "$PASSWORD" -file "$CERTS_DIR/ca.pem" -noprompt

echo ""
echo "=== Done! Generated files ==="
ls -la "$CERTS_DIR"/*.p12 "$CERTS_DIR"/*.pem
echo ""
echo "Password for all stores: $PASSWORD"
