# Iteration 13: API Security

> "Your services are talking to each other — but who's listening?"

Every API we've built so far is wide open. Anyone can call any endpoint. In production, that's a disaster waiting to happen. This iteration adds security — progressing from simple to enterprise-grade across 4 parts.

---

## What You'll Learn

| Part | Approach | Key Concepts |
|------|----------|-------------|
| **A** | Basic Auth | Spring Security 6, SecurityFilterChain, BCrypt, role-based access |
| **B** | JWT | Stateless tokens, HMAC-SHA256, token propagation between services |
| **C** | OAuth2/Keycloak | External Identity Provider, JWKS, TokenRelay, OIDC |
| **D** | mTLS | Mutual TLS, X.509 certificates, transport-layer security |

---

## Architecture Overview

```
                    ┌─────────────┐
                    │   Client    │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │ API Gateway │  ← Security enforced here
                    │  :9000      │
                    └──┬──────┬───┘
                       │      │
              ┌────────▼┐  ┌──▼────────┐
              │ Greeting │  │   Time    │
              │ Service  │  │  Service  │
              │  :9001   │  │   :9002   │
              └──────────┘  └───────────┘
```

Each part uses the same 3 services but adds a different security layer.

---

## Part A: Basic Auth (Spring Security Fundamentals)

### The Idea

The gateway enforces HTTP Basic Authentication. Backend services are internal (no security) — they trust the gateway. The gateway propagates the authenticated user's identity via headers.

```
Client ──Basic Auth──▶ Gateway ──X-Authenticated-User──▶ Services
```

### Run It

```bash
cd part-a-basic-auth
docker compose up --build
```

### Test It

```bash
# 1. No credentials → 401
curl -s -o /dev/null -w "%{http_code}" localhost:9000/greeting
# 401

# 2. Valid credentials → 200
curl -u user:password localhost:9000/greeting
# {"message":"Hello from Greeting Service!","host":"..."}

# 3. Admin user → 200
curl -u admin:admin123 localhost:9000/greeting
# {"message":"Hello from Greeting Service!","host":"..."}

# 4. See who you are (header propagation)
curl -u user:password localhost:9000/greeting/whoami
# {"authenticatedUser":"user","roles":"ROLE_USER","host":"..."}

curl -u admin:admin123 localhost:9000/greeting/whoami
# {"authenticatedUser":"admin","roles":"ROLE_USER,ROLE_ADMIN","host":"..."}

# 5. Time service via gateway
curl -u user:password localhost:9000/time
curl -u user:password localhost:9000/time/with-greeting
```

### Key Files

| File | What It Does |
|------|-------------|
| `api-gateway/SecurityConfig.java` | `@EnableWebFluxSecurity`, in-memory users, BCrypt, role-based routes |
| `api-gateway/AddUserHeaderFilter.java` | Propagates `X-Authenticated-User` and `X-User-Roles` headers downstream |
| `greeting-service/GreetingController.java` | `/greeting/whoami` reads the propagated headers |

### Clean Up

```bash
docker compose down -v
```

---

## Part B: JWT Authentication (Stateless Tokens)

### The Idea

The most common microservices auth pattern. The gateway has a `/auth/login` endpoint that returns a JWT. **Every service validates the JWT independently** using a shared secret (HMAC-SHA256). No session state needed.

```
Client ──POST /auth/login──▶ Gateway ──returns JWT──▶ Client
Client ──Bearer <JWT>──▶ Gateway ──forwards JWT──▶ Services (each validates independently)
```

### Run It

```bash
cd part-b-jwt
docker compose up --build
```

### Test It

```bash
# 1. Login to get a JWT
TOKEN=$(curl -s -X POST localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password"}' | jq -r '.token')

echo $TOKEN

# 2. Use the token
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting
# {"message":"Hello from Greeting Service!","authenticatedUser":"user","host":"..."}

# 3. Who am I?
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting/whoami
# {"user":"user","roles":"ROLE_USER","host":"..."}

# 4. No token → 401
curl -s -o /dev/null -w "%{http_code}" localhost:9000/greeting
# 401

# 5. Token propagation: time → greeting (JWT forwarded automatically)
curl -H "Authorization: Bearer $TOKEN" localhost:9000/time/with-greeting

# 6. Admin login
ADMIN_TOKEN=$(curl -s -X POST localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

curl -H "Authorization: Bearer $ADMIN_TOKEN" localhost:9000/greeting/whoami
# {"user":"admin","roles":"ROLE_USER,ROLE_ADMIN","host":"..."}
```

### Key Files

| File | What It Does |
|------|-------------|
| `api-gateway/JwtUtil.java` | Generate + validate JWT (HMAC-SHA256) |
| `api-gateway/AuthController.java` | `POST /auth/login` → returns JWT |
| `api-gateway/JwtAuthenticationFilter.java` | Reactive WebFilter — validates Bearer token |
| `greeting-service/JwtAuthenticationFilter.java` | Servlet `OncePerRequestFilter` — validates JWT independently |
| `time-service/JwtPropagationInterceptor.java` | `ClientHttpRequestInterceptor` — forwards JWT on outgoing calls |

### Shared Secret

All 3 services share the same JWT secret via the `JWT_SECRET` environment variable in `docker-compose.yml`. In production, use a secrets manager.

### Clean Up

```bash
docker compose down -v
```

---

## Part C: OAuth2 with Keycloak (Enterprise Standard)

### The Idea

Full OAuth2/OIDC with an external Identity Provider — what you'd use in production at scale. Keycloak issues tokens (asymmetric RSA keys). Services are OAuth2 Resource Servers that validate tokens via Keycloak's JWKS endpoint. No shared secrets needed.

```
Client ──password grant──▶ Keycloak ──JWT (RSA-signed)──▶ Client
Client ──Bearer <JWT>──▶ Gateway ──TokenRelay──▶ Services ──JWKS validation──▶ Keycloak
```

### Run It

```bash
cd part-c-oauth2-keycloak
docker compose up --build
# Wait for Keycloak to be healthy (~60s on first run)
```

### Test It

```bash
# 1. Get token from Keycloak (resource owner password grant)
TOKEN=$(curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token \
  -d "client_id=demo-app" \
  -d "username=user" \
  -d "password=password" \
  -d "grant_type=password" | jq -r '.access_token')

echo $TOKEN

# 2. Call via gateway (TokenRelay forwards the token)
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting
# {"message":"Hello from Greeting Service!","authenticatedUser":"user","host":"..."}

# 3. Detailed user info
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting/whoami

# 4. No token → 401
curl -s -o /dev/null -w "%{http_code}" localhost:9000/greeting
# 401

# 5. Token propagation via gateway
curl -H "Authorization: Bearer $TOKEN" localhost:9000/time/with-greeting

# 6. Admin token
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token \
  -d "client_id=demo-app" \
  -d "username=admin" \
  -d "password=admin123" \
  -d "grant_type=password" | jq -r '.access_token')

curl -H "Authorization: Bearer $ADMIN_TOKEN" localhost:9000/greeting/whoami

# 7. Keycloak admin console
# Open http://localhost:8180 — login: admin/admin
```

### Key Files

| File | What It Does |
|------|-------------|
| `keycloak/realm-export.json` | Pre-configured realm with clients, users, roles |
| `api-gateway/SecurityConfig.java` | `oauth2ResourceServer().jwt()` with Keycloak role converter |
| `api-gateway/KeycloakRoleConverter.java` | Extracts roles from `realm_access.roles` claim |
| `greeting-service/SecurityConfig.java` | Servlet OAuth2 Resource Server with JWKS validation |
| `time-service/TokenPropagationInterceptor.java` | Forwards Bearer token on service-to-service calls |
| `application-docker.properties` | Points `issuer-uri` to `http://keycloak:8080/realms/demo` |

### How Token Validation Works (No Shared Secret!)

```
1. Keycloak signs tokens with its RSA private key
2. Services fetch Keycloak's public key via JWKS: GET /realms/demo/protocol/openid-connect/certs
3. Services validate the JWT signature locally — no call to Keycloak per request
4. Keys are cached and refreshed automatically
```

### Clean Up

```bash
docker compose down -v
```

---

## Part D: mTLS (Mutual TLS)

### The Idea

Transport-layer security. Every service has its own X.509 certificate. Services require a valid **client certificate** for incoming connections. This means even if someone gets on the network, they can't call services without the right cert.

```
Client ──HTTPS + CA cert──▶ Gateway ──mTLS (presents client cert)──▶ Services
                                                                      ↑
                                                          Rejects connections
                                                          without valid cert
```

### Step 1: Generate Certificates

```bash
cd part-d-mtls
./certs/generate-certs.sh
```

This creates:
- `ca.p12` / `ca.pem` — Certificate Authority
- `api-gateway.p12` — Gateway's keystore (identity + signed cert)
- `greeting-service.p12` — Greeting service's keystore
- `time-service.p12` — Time service's keystore
- `truststore.p12` — Shared truststore (contains CA cert)

### Step 2: Run It

```bash
docker compose up --build
```

### Test It

```bash
# 1. Via gateway (gateway presents its client cert to downstream services)
curl --cacert certs/ca.pem https://localhost:9000/greeting
# {"message":"Hello from Greeting Service (mTLS)!","clientCert":"CN=api-gateway,...","host":"..."}

# 2. Direct call WITHOUT client cert → FAILS (SSL handshake error)
curl --cacert certs/ca.pem https://localhost:9001/greeting
# curl: (56) SSL peer handshake failed

# 3. Time service calls greeting with its own client cert
curl --cacert certs/ca.pem https://localhost:9000/time/with-greeting
# Both services authenticated each other via mTLS!

# 4. Plain HTTP → FAILS (services only accept HTTPS)
curl http://localhost:9001/greeting
# Connection refused / reset
```

### Key Files

| File | What It Does |
|------|-------------|
| `certs/generate-certs.sh` | Generates CA, service certs, truststore using `keytool` |
| `greeting-service/application-docker.properties` | `server.ssl.client-auth=need` — requires client cert |
| `time-service/MtlsRestTemplateConfig.java` | Configures RestTemplate with SSLContext (presents client cert) |
| `api-gateway/application-docker.properties` | Gateway HTTPS + mTLS client config for downstream |

### How mTLS Differs from Regular HTTPS

| | Regular HTTPS | mTLS |
|---|---|---|
| Client verifies server | Yes | Yes |
| Server verifies client | No | **Yes** |
| Client needs certificate | No | **Yes** |
| Use case | Browser → server | Service → service |

### Clean Up

```bash
docker compose down -v
```

---

## Comparison: When to Use What?

| Approach | Complexity | Use Case | Token Type |
|----------|-----------|----------|-----------|
| **Basic Auth** | Low | Internal tools, simple APIs | Username:Password (every request) |
| **JWT (self-issued)** | Medium | Microservices, mobile apps | Self-signed HMAC token |
| **OAuth2/Keycloak** | High | Enterprise, multi-app SSO | RSA-signed JWT from IdP |
| **mTLS** | High | Service mesh, zero-trust | X.509 certificates |

### Production at NatWest?

You'd combine them:
- **OAuth2/Keycloak** for user authentication (who is calling?)
- **mTLS** for service-to-service transport (is this a trusted service?)
- **RBAC** for authorization (what can they do?)

---

## Quick Reference

```bash
# Part A: Basic Auth
cd part-a-basic-auth && docker compose up --build
curl -u user:password localhost:9000/greeting

# Part B: JWT
cd part-b-jwt && docker compose up --build
TOKEN=$(curl -s -X POST localhost:9000/auth/login -H "Content-Type: application/json" -d '{"username":"user","password":"password"}' | jq -r '.token')
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting

# Part C: OAuth2/Keycloak
cd part-c-oauth2-keycloak && docker compose up --build
TOKEN=$(curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token -d "client_id=demo-app&username=user&password=password&grant_type=password" | jq -r '.access_token')
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting

# Part D: mTLS
cd part-d-mtls && ./certs/generate-certs.sh && docker compose up --build
curl --cacert certs/ca.pem https://localhost:9000/greeting
```

---

## Discussion Questions

1. **Part A**: Why is Basic Auth a bad choice for microservices in production? (Hint: credentials on every request)
2. **Part B**: What happens if the JWT secret is leaked? How does this compare to OAuth2's approach?
3. **Part C**: Why does Keycloak use RSA (asymmetric) keys instead of HMAC (symmetric)? What's the advantage?
4. **Part D**: If you have mTLS, do you still need JWT? What does each protect against?
5. **Defense in depth**: How would you combine Parts C and D for maximum security?
