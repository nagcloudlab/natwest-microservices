# Iteration 13: API Security for Microservices

> **Training Module for NatWest Java Developers**
>
> _From wide open to locked down -- four progressive approaches to securing
> microservices, from HTTP Basic to mutual TLS._

---

## Table of Contents

1. [The Problem](#the-problem)
2. [The 4 Layers of API Security](#the-4-layers-of-api-security)
3. [Our Architecture](#our-architecture)
4. [Part A: Basic Auth (Spring Security Fundamentals)](#part-a-basic-auth)
5. [Part B: JWT Authentication (Stateless Tokens)](#part-b-jwt-authentication)
6. [Part C: OAuth2 with Keycloak (Enterprise Identity Provider)](#part-c-oauth2-with-keycloak)
7. [Part D: mTLS (Mutual TLS)](#part-d-mtls)
8. [How to Run Each Part](#how-to-run-each-part)
9. [Security Comparison Matrix](#security-comparison-matrix)
10. [Token Propagation Deep Dive](#token-propagation-deep-dive)
11. [What Changed Per Part (Code Walkthrough)](#what-changed-per-part)
12. [Key Concepts Reference](#key-concepts-reference)
13. [Production at NatWest](#production-at-natwest)
14. [Common Pitfalls](#common-pitfalls)
15. [Key Takeaways](#key-takeaways)

---

## The Problem

Every service we've built so far is **wide open**. No authentication, no authorization, no encryption. Anyone who can reach the network can call any endpoint.

```bash
# Anyone can do this. No credentials. No identity. Nothing.
curl http://localhost:9000/greeting
curl http://localhost:9000/time
curl http://localhost:9001/greeting    # Direct to service, bypassing the gateway
```

### Why This Is Dangerous

| Attack | What Happens | Example |
|--------|-------------|---------|
| **Unauthorized access** | Anyone can call any API | A competitor scrapes your pricing data |
| **Data theft** | Sensitive data returned to unknown callers | Customer PII exposed without authentication |
| **Privilege escalation** | No role enforcement | A regular user accesses admin endpoints |
| **Service impersonation** | Any service can call any other | A rogue container calls the payment service |
| **Eavesdropping** | Plain HTTP traffic is readable | Network sniffer captures auth tokens in transit |

### The Questions We Need to Answer

| Question | Security Layer |
|----------|---------------|
| **Who is calling?** | Authentication (Parts A, B, C) |
| **What can they do?** | Authorization / Roles (Parts A, B, C) |
| **Who issued the credential?** | Identity Provider (Part C) |
| **Is this a trusted service?** | Transport Security (Part D) |

No single mechanism answers all four. That's why this iteration has four parts.

---

## The 4 Layers of API Security

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         LAYER 4: mTLS                                  │
│              "Is this connection from a trusted service?"               │
│              Transport-level · X.509 certificates                      │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                      LAYER 3: OAuth2 / OIDC                      │  │
│  │           "Who issued this token? Can I verify it?"               │  │
│  │           External IdP · Asymmetric keys · JWKS                  │  │
│  │  ┌─────────────────────────────────────────────────────────────┐  │  │
│  │  │                    LAYER 2: JWT                              │  │  │
│  │  │         "Is this token valid and not expired?"               │  │  │
│  │  │         Self-issued · Shared secret · Stateless              │  │  │
│  │  │  ┌───────────────────────────────────────────────────────┐  │  │  │
│  │  │  │               LAYER 1: Basic Auth                     │  │  │  │
│  │  │  │        "Do you have a username and password?"          │  │  │  │
│  │  │  │        Server-side session · BCrypt · Roles            │  │  │  │
│  │  │  └───────────────────────────────────────────────────────┘  │  │  │
│  │  └─────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

Each layer builds on the concepts from the previous one. In production, you combine them (typically OAuth2 + mTLS).

---

## Our Architecture

All four parts use the same three services with different security layers:

```
                         ┌──────────┐
                         │  Client  │
                         │  (curl)  │
                         └────┬─────┘
                              │
                    ┌─────────▼─────────┐
                    │    API Gateway     │   ← Security enforced here
                    │     :9000         │
                    └───┬───────────┬───┘
                        │           │
              ┌─────────▼──┐   ┌───▼──────────┐
              │  Greeting   │   │    Time       │
              │  Service    │   │   Service     │
              │   :9001     │   │    :9002      │
              └─────────────┘   └──────┬───────┘
                                       │
                              calls /greeting
                              (token propagation)
```

**Key design:** No Eureka, no Config Server. Pure security focus. Direct HTTP URLs between services.

### What Changes Per Part

| Component | Part A | Part B | Part C | Part D |
|-----------|--------|--------|--------|--------|
| **Gateway** | Spring Security + Basic Auth | JWT issuer + filter | OAuth2 Resource Server | HTTPS + mTLS client |
| **Greeting** | No security (internal) | JWT filter (independent) | OAuth2 Resource Server | mTLS server |
| **Time** | No security (internal) | JWT filter + propagation | OAuth2 RS + propagation | mTLS server + client |
| **Auth source** | In-memory users | In-memory users + JWT | Keycloak IdP | X.509 certificates |
| **Token type** | Base64(user:pass) | HMAC-SHA256 JWT | RSA-signed JWT | X.509 cert |
| **Stateless?** | No (per-request auth) | Yes | Yes | Yes (cert-based) |

---

## Part A: Basic Auth

### Concept

HTTP Basic Authentication is the simplest security mechanism. The client sends `username:password` (Base64-encoded) in every request. The server validates against stored credentials.

```
Client                          Gateway                      Greeting Service
  │                               │                               │
  │ Authorization: Basic          │                               │
  │ dXNlcjpwYXNzd29yZA==         │                               │
  ├──────────────────────────────►│                               │
  │                               │ 1. Decode Base64              │
  │                               │ 2. Verify password (BCrypt)   │
  │                               │ 3. Check role                 │
  │                               │                               │
  │                               │ X-Authenticated-User: user    │
  │                               │ X-User-Roles: ROLE_USER       │
  │                               ├──────────────────────────────►│
  │                               │                               │
  │                               │◄──────────────────────────────┤
  │◄──────────────────────────────┤                               │
  │         200 OK                │                               │
```

### What Spring Security 6 Does

When you add `spring-boot-starter-security` to a Spring WebFlux (Gateway) application, you configure a `SecurityWebFilterChain`:

```java
@Bean
public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
    return http
        .csrf(ServerHttpSecurity.CsrfSpec::disable)           // Disable CSRF for API
        .authorizeExchange(exchanges -> exchanges
            .pathMatchers("/actuator/health").permitAll()      // Health check is public
            .pathMatchers("/greeting/admin/**").hasRole("ADMIN") // Role-based access
            .anyExchange().authenticated()                     // Everything else needs auth
        )
        .httpBasic(httpBasic -> {})                           // Enable HTTP Basic
        .build();
}
```

### Users Configured

| Username | Password | Roles | Hashed With |
|----------|----------|-------|------------|
| `user` | `password` | `ROLE_USER` | BCrypt |
| `admin` | `admin123` | `ROLE_USER`, `ROLE_ADMIN` | BCrypt |

### Header Propagation

The gateway propagates identity downstream via a `GlobalFilter`:

```java
@Component
public class AddUserHeaderFilter implements GlobalFilter, Ordered {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(Authentication::isAuthenticated)
            .flatMap(auth -> {
                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-Authenticated-User", auth.getName())
                    .header("X-User-Roles", roles)
                    .build();
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            });
    }
}
```

Backend services read these headers — they trust the gateway because they're on an internal Docker network.

### Why Basic Auth Is Not Enough for Microservices

| Problem | Explanation |
|---------|-------------|
| **Credentials on every request** | Password travels over the wire with every call |
| **No token expiry** | Password is valid until manually changed |
| **Server-side validation** | Every request hits the user store |
| **No claims/metadata** | Cannot carry roles, email, etc. in the credential itself |
| **Backend services trust headers** | Anyone on the internal network can fake `X-Authenticated-User` |

This motivates Part B — JWT tokens.

---

## Part B: JWT Authentication

### Concept

JSON Web Tokens (JWT) are self-contained, signed tokens. The gateway issues them, and **every service validates independently** using a shared secret. No session state. No database lookup per request.

```
┌─────────┐                   ┌──────────┐                  ┌──────────┐
│ Client  │                   │ Gateway  │                  │ Greeting │
└────┬────┘                   └────┬─────┘                  └────┬─────┘
     │                             │                              │
     │ POST /auth/login            │                              │
     │ {"username":"user",         │                              │
     │  "password":"password"}     │                              │
     ├────────────────────────────►│                              │
     │                             │ Validate credentials         │
     │                             │ Generate JWT (HMAC-SHA256)   │
     │◄────────────────────────────┤                              │
     │ {"token":"eyJhbGci..."}     │                              │
     │                             │                              │
     │ GET /greeting               │                              │
     │ Authorization: Bearer eyJ...│                              │
     ├────────────────────────────►│                              │
     │                             │ Validate JWT signature       │
     │                             │ Extract user + roles         │
     │                             │                              │
     │                             │ Authorization: Bearer eyJ... │
     │                             ├─────────────────────────────►│
     │                             │                              │ Validate JWT
     │                             │                              │ independently
     │                             │◄─────────────────────────────┤
     │◄────────────────────────────┤                              │
     │       200 OK                │                              │
```

### JWT Structure

A JWT has three parts separated by dots: `HEADER.PAYLOAD.SIGNATURE`

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyIiwicm9sZXMiOlsiUk9MRV9VU0VSIl0sImlhdCI6MTcwOTkwMTIwMCwiZXhwIjoxNzA5OTA0ODAwfQ.SIGNATURE
```

**Decoded payload:**

```json
{
  "sub": "user",                    // Who this token is for
  "roles": ["ROLE_USER"],           // What they can do
  "iat": 1709901200,                // Issued at
  "exp": 1709904800                 // Expires at (1 hour later)
}
```

### Key Implementation Details

**Gateway (Reactive WebFlux):**

| Class | Purpose |
|-------|---------|
| `JwtUtil.java` | Generate + validate JWT using JJWT library (HMAC-SHA256) |
| `AuthController.java` | `POST /auth/login` — validates credentials, returns JWT |
| `JwtAuthenticationFilter.java` | Reactive `WebFilter` — extracts Bearer token, validates, sets SecurityContext |
| `SecurityConfig.java` | Permits `/auth/login`, disables Basic Auth and form login |

**Backend Services (Servlet):**

| Class | Purpose |
|-------|---------|
| `JwtAuthenticationFilter.java` | Servlet `OncePerRequestFilter` — validates JWT independently (same shared secret) |
| `SecurityConfig.java` | Stateless session, JWT filter before `UsernamePasswordAuthenticationFilter` |

**Time Service (extra):**

| Class | Purpose |
|-------|---------|
| `JwtPropagationInterceptor.java` | `ClientHttpRequestInterceptor` on RestTemplate — copies `Authorization` header from incoming request to outgoing call |

### Shared Secret

All three services share the same HMAC key via the `JWT_SECRET` environment variable:

```yaml
# docker-compose.yml
environment:
  JWT_SECRET: ThisIsAVeryLongSecretKeyForHMACSHA256SigningThatMustBeAtLeast256Bits!
```

### Why JWT Alone Is Not Enough for Enterprise

| Problem | Explanation |
|---------|-------------|
| **Shared secret** | If leaked, anyone can forge tokens. All services must rotate simultaneously |
| **No revocation** | A valid JWT works until it expires — you can't invalidate a single token |
| **Self-issued** | No central authority. Who do you trust? Each app manages its own users |
| **Symmetric keys** | HMAC means the key that verifies can also sign — any service can forge tokens |
| **No SSO** | Each application has its own login. Users manage N passwords |

This motivates Part C — an external Identity Provider.

---

## Part C: OAuth2 with Keycloak

### Concept

OAuth2 with OpenID Connect (OIDC) is the enterprise standard. An external **Identity Provider** (Keycloak) manages users, passwords, and roles. It issues tokens signed with **RSA private keys**. Services validate tokens using Keycloak's **public keys** (JWKS endpoint). No shared secrets.

```
┌─────────┐          ┌──────────┐          ┌──────────┐         ┌──────────┐
│ Client  │          │ Keycloak │          │ Gateway  │         │ Greeting │
│         │          │  :8180   │          │  :9000   │         │  :9001   │
└────┬────┘          └────┬─────┘          └────┬─────┘         └────┬─────┘
     │                    │                     │                     │
     │ POST /token        │                     │                     │
     │ grant_type=password│                     │                     │
     │ username=user      │                     │                     │
     ├───────────────────►│                     │                     │
     │                    │ Validate password   │                     │
     │                    │ Sign JWT with RSA   │                     │
     │◄───────────────────┤ private key         │                     │
     │ {access_token:...} │                     │                     │
     │                    │                     │                     │
     │ GET /greeting      │                     │                     │
     │ Bearer <JWT>       │                     │                     │
     ├──────────────────────────────────────────►                     │
     │                    │                     │ Fetch public key    │
     │                    │◄────────────────────┤ GET /certs (JWKS)  │
     │                    │────────────────────►│                     │
     │                    │ {public key}        │ Verify signature    │
     │                    │                     │                     │
     │                    │                     │ TokenRelay: forward │
     │                    │                     │ Bearer <JWT>        │
     │                    │                     ├────────────────────►│
     │                    │                     │                     │ Validate via
     │                    │                     │                     │ JWKS (cached)
     │                    │                     │◄────────────────────┤
     │◄─────────────────────────────────────────┤                     │
     │       200 OK       │                     │                     │
```

### Keycloak Realm Configuration

The realm is pre-configured via `realm-export.json`:

**Realm:** `demo`

**Clients:**

| Client ID | Type | Purpose |
|-----------|------|---------|
| `api-gateway` | Confidential | For server-side flows (has a secret) |
| `demo-app` | Public | For testing with curl (no secret needed) |

**Users:**

| Username | Password | Realm Roles |
|----------|----------|-------------|
| `user` | `password` | `ROLE_USER` |
| `admin` | `admin123` | `ROLE_USER`, `ROLE_ADMIN` |

**Protocol Mappers (on each client):**

| Mapper | Claim | Purpose |
|--------|-------|---------|
| `username` | `preferred_username` | User's login name in the token |
| `email` | `email` | User's email in the token |
| `realm roles` | `realm_access.roles` | User's roles in the token |

### RSA vs HMAC: Why It Matters

| | HMAC (Part B) | RSA (Part C) |
|---|---|---|
| Key type | Symmetric (one shared secret) | Asymmetric (private + public) |
| Who can sign? | Anyone with the secret | Only Keycloak (has private key) |
| Who can verify? | Anyone with the secret | Anyone (public key is, well, public) |
| Secret distribution | Must share with every service | No secrets to distribute |
| Compromise impact | Attacker can forge tokens | Attacker can only verify, not forge |
| Key rotation | All services must update simultaneously | Keycloak rotates; services fetch new keys via JWKS |

### The Issuer Mismatch Problem

A common gotcha when running Keycloak in Docker:

```
Token is issued by:     http://localhost:8180/realms/demo    (how client sees Keycloak)
Services validate via:  http://keycloak:8080/realms/demo     (how Docker services see Keycloak)
```

The `iss` (issuer) claim in the JWT says `localhost:8180`, but the service expects `keycloak:8080`. Spring rejects the token with `"The iss claim is not valid"`.

**Solution:** Use `jwk-set-uri` instead of `issuer-uri`:

```properties
# Instead of this (validates issuer claim):
# spring.security.oauth2.resourceserver.jwt.issuer-uri=http://keycloak:8080/realms/demo

# Use this (only fetches keys, doesn't validate issuer):
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/demo/protocol/openid-connect/certs
```

### KeycloakRoleConverter

Keycloak puts roles in a nested structure (`realm_access.roles`), but Spring Security expects flat `GrantedAuthority` objects. The converter bridges this:

```java
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        List<String> roles = (List<String>) realmAccess.get("roles");
        // Convert ["ROLE_USER", "ROLE_ADMIN"] to GrantedAuthority objects
        Collection<GrantedAuthority> authorities = roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
        return new JwtAuthenticationToken(jwt, authorities,
            jwt.getClaimAsString("preferred_username"));
    }
}
```

---

## Part D: mTLS

### Concept

Mutual TLS (mTLS) operates at the **transport layer**. Every service has its own X.509 certificate signed by a trusted Certificate Authority (CA). When two services connect:

1. The **server** presents its certificate (standard TLS)
2. The **client** also presents its certificate (the "mutual" part)
3. Both sides verify the other's cert was signed by the trusted CA

```
┌─────────┐                    ┌──────────┐                   ┌──────────┐
│ Client  │                    │ Gateway  │                   │ Greeting │
│ (curl)  │                    │          │                   │          │
│         │                    │ Has:     │                   │ Has:     │
│ Trusts: │                    │ • api-   │                   │ • greeting│
│ • CA    │                    │   gateway│                   │   -service│
│   cert  │                    │   .p12   │                   │   .p12   │
│         │                    │ • trust- │                   │ • trust- │
│         │                    │   store  │                   │   store  │
└────┬────┘                    └────┬─────┘                   └────┬─────┘
     │                              │                              │
     │ TLS Handshake                │                              │
     │ 1. Server sends cert         │                              │
     │    (CN=api-gateway)          │                              │
     │ 2. Client verifies with CA   │                              │
     ├─────────────────────────────►│                              │
     │                              │ mTLS Handshake               │
     │                              │ 1. Server: CN=greeting-svc   │
     │                              │ 2. Client: CN=api-gateway    │
     │                              │ 3. Both verify with CA       │
     │                              ├─────────────────────────────►│
     │                              │                              │
     │                              │ greeting sees:               │
     │                              │ clientCert=CN=api-gateway    │
     │                              │◄─────────────────────────────┤
     │◄─────────────────────────────┤                              │
     │ {"clientCert":               │                              │
     │  "CN=api-gateway"}           │                              │
```

### Certificate Generation

The `generate-certs.sh` script creates:

```
certs/
├── ca.p12              ← Certificate Authority keystore
├── ca.pem              ← CA certificate (public, for curl --cacert)
├── api-gateway.p12     ← Gateway's identity (private key + CA-signed cert)
├── greeting-service.p12 ← Greeting's identity
├── time-service.p12    ← Time's identity
└── truststore.p12      ← Shared truststore (contains only the CA cert)
```

**Process per service:**

```
1. keytool -genkeypair        → Generate RSA keypair (self-signed)
2. keytool -certreq           → Create Certificate Signing Request (CSR)
3. keytool -gencert (CA)      → CA signs the CSR → signed certificate
4. keytool -importcert (CA)   → Import CA cert into service keystore
5. keytool -importcert (cert) → Import signed cert into service keystore
```

### Server-Side Configuration

Each service requires mTLS for incoming connections:

```properties
# TLS server (present our certificate)
server.ssl.enabled=true
server.ssl.key-store=/app/certs/greeting-service.p12
server.ssl.key-store-password=changeit
server.ssl.key-alias=greeting-service

# mTLS: REQUIRE client certificate
server.ssl.client-auth=need
server.ssl.trust-store=/app/certs/truststore.p12
server.ssl.trust-store-password=changeit
```

The `client-auth=need` setting is the key — it tells the server to demand a client certificate during the TLS handshake.

### Client-Side Configuration (Time → Greeting)

Time-service calls greeting-service and must present its own certificate:

```java
@Configuration
public class MtlsRestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() throws Exception {
        // Load time-service's keystore (our identity)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new FileInputStream(keystorePath), password);

        // Load truststore (who we trust — the CA)
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(new FileInputStream(truststorePath), password);

        // Build SSL context with both
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, null);

        // Configure HttpClient with the SSL context
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(/* with SSL socket factory */)
            .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}
```

### Health Checks with mTLS

When SSL is enabled on the main port, health checks can't use plain HTTP. Each service exposes the actuator on a **separate non-SSL port**:

```properties
management.server.port=9011          # Separate port
management.server.ssl.enabled=false  # No TLS on health endpoint
```

| Service | HTTPS Port | Health Port |
|---------|-----------|-------------|
| API Gateway | 9000 | 9010 |
| Greeting | 9001 | 9011 |
| Time | 9002 | 9012 |

### mTLS vs Regular HTTPS

| | Regular HTTPS | mTLS |
|---|---|---|
| Client verifies server identity | Yes | Yes |
| Server verifies client identity | **No** | **Yes** |
| Client needs a certificate | No | **Yes** |
| Protection against impersonation | One-way | **Both ways** |
| Use case | Browser → server | **Service → service** |

---

## How to Run Each Part

### Prerequisites

- Docker Desktop (or Docker Engine + Docker Compose v2)
- `curl` and `jq` installed
- For Part D: `keytool` (included with JDK)

### Part A: Basic Auth

```bash
cd simple-demos/iteration-13-api-security/part-a-basic-auth
docker compose up --build

# Test
curl -u user:password localhost:9000/greeting
curl -u admin:admin123 localhost:9000/greeting/whoami
curl -u user:password localhost:9000/time/with-greeting

# Cleanup
docker compose down -v
```

### Part B: JWT

```bash
cd simple-demos/iteration-13-api-security/part-b-jwt
docker compose up --build

# Login
TOKEN=$(curl -s -X POST localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password"}' | jq -r '.token')

# Test
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting/whoami
curl -H "Authorization: Bearer $TOKEN" localhost:9000/time/with-greeting

# Cleanup
docker compose down -v
```

### Part C: OAuth2/Keycloak

```bash
cd simple-demos/iteration-13-api-security/part-c-oauth2-keycloak
docker compose up --build    # Keycloak takes ~60s on first start

# Get token from Keycloak
TOKEN=$(curl -s -X POST http://localhost:8180/realms/demo/protocol/openid-connect/token \
  -d "client_id=demo-app" \
  -d "username=user" \
  -d "password=password" \
  -d "grant_type=password" | jq -r '.access_token')

# Test
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting
curl -H "Authorization: Bearer $TOKEN" localhost:9000/greeting/whoami
curl -H "Authorization: Bearer $TOKEN" localhost:9000/time/with-greeting

# Decode token to see claims
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq .

# Keycloak admin console: http://localhost:8180 (admin/admin)

# Cleanup
docker compose down -v
```

### Part D: mTLS

```bash
cd simple-demos/iteration-13-api-security/part-d-mtls

# Step 1: Generate certificates
./certs/generate-certs.sh

# Step 2: Start
docker compose up --build

# Test (--cacert tells curl to trust our CA)
curl --cacert certs/ca.pem https://localhost:9000/greeting
curl --cacert certs/ca.pem https://localhost:9000/time/with-greeting

# Verify mTLS enforcement: direct call WITHOUT client cert fails
curl --cacert certs/ca.pem https://localhost:9001/greeting   # SSL error!

# Cleanup
docker compose down -v
```

---

## Security Comparison Matrix

| Aspect | Part A: Basic Auth | Part B: JWT | Part C: OAuth2/Keycloak | Part D: mTLS |
|--------|-------------------|-------------|------------------------|-------------|
| **Complexity** | Low | Medium | High | High |
| **Credential type** | Username:Password | HMAC-SHA256 token | RSA-signed JWT | X.509 certificate |
| **Stateless** | No | Yes | Yes | Yes |
| **Token expiry** | No (password-based) | Yes (exp claim) | Yes (exp claim) | Yes (cert validity) |
| **Can carry claims** | No | Yes (roles, etc.) | Yes (rich claims) | Yes (CN, SAN) |
| **Revocation** | Change password | Wait for expiry | Keycloak session mgmt | Revoke cert (CRL/OCSP) |
| **Key management** | BCrypt hashes | Shared HMAC secret | Keycloak manages keys | CA + per-service certs |
| **SSO capable** | No | No | Yes | No |
| **Protects transport** | No | No | No | **Yes** |
| **Spring dependency** | `starter-security` | `starter-security` + JJWT | `starter-oauth2-resource-server` | Server SSL config |
| **Backend services need security?** | No (trust gateway) | Yes (validate JWT) | Yes (validate JWT) | Yes (require client cert) |
| **Use case** | Internal tools | Mobile/SPA apps | Enterprise, multi-app | Service mesh, zero-trust |

---

## Token Propagation Deep Dive

The hardest part of microservices security is **propagating identity** across service-to-service calls. Here's how each part handles it:

### Part A: Header Propagation

```
Gateway                           Time Service              Greeting Service
   │                                 │                           │
   │ X-Authenticated-User: user      │                           │
   │ X-User-Roles: ROLE_USER         │                           │
   ├────────────────────────────────►│                           │
   │                                 │ (reads headers, but       │
   │                                 │  does NOT forward them    │
   │                                 │  to greeting-service)     │
   │                                 │                           │
   │                                 │ http://greeting:9001      │
   │                                 │ (no auth headers)         │
   │                                 ├──────────────────────────►│
```

**Limitation:** Custom headers aren't forwarded by RestTemplate by default. Backend services don't enforce security anyway.

### Part B: JWT Interceptor

```
Gateway                           Time Service              Greeting Service
   │                                 │                           │
   │ Authorization: Bearer <JWT>     │                           │
   ├────────────────────────────────►│                           │
   │   (Gateway forwards all         │                           │
   │    headers by default)          │ JwtPropagation            │
   │                                 │ Interceptor:              │
   │                                 │ 1. Get incoming request   │
   │                                 │ 2. Read Authorization hdr │
   │                                 │ 3. Copy to outgoing call  │
   │                                 │                           │
   │                                 │ Authorization: Bearer <JWT│
   │                                 ├──────────────────────────►│
   │                                 │                           │ Validates JWT
   │                                 │                           │ independently!
```

### Part C: TokenRelay + Interceptor

```
Gateway (TokenRelay filter)       Time Service              Greeting Service
   │                                 │                           │
   │ Authorization: Bearer <JWT>     │                           │
   │ (Keycloak RSA-signed)           │                           │
   ├────────────────────────────────►│                           │
   │   TokenRelay filter             │                           │
   │   automatically forwards        │ TokenPropagation          │
   │   the Bearer token              │ Interceptor               │
   │                                 │ (same as Part B)          │
   │                                 │                           │
   │                                 │ Authorization: Bearer <JWT│
   │                                 ├──────────────────────────►│
   │                                 │                           │ Validates via
   │                                 │                           │ JWKS endpoint
```

### Part D: Certificate Identity

```
Gateway (cert: CN=api-gateway)    Time Service              Greeting Service
   │                                 │                           │
   │ mTLS handshake                  │                           │
   │ Client cert: CN=api-gateway     │                           │
   ├────────────────────────────────►│                           │
   │                                 │ mTLS handshake            │
   │                                 │ Client cert:              │
   │                                 │ CN=time-service           │
   │                                 ├──────────────────────────►│
   │                                 │                           │ Sees caller:
   │                                 │                           │ CN=time-service
   │                                 │                           │ (NOT api-gateway!)
```

**Key insight in Part D:** Each hop authenticates with its **own** certificate. Greeting-service sees `CN=time-service` as the caller, not `CN=api-gateway`. This provides true per-service identity.

---

## What Changed Per Part

### Files Per Part

| Part | Gateway Files | Greeting Files | Time Files | Other | Total |
|------|-------------|---------------|------------|-------|-------|
| **A** | 5 (pom, app, security, filter, props) | 4 (pom, app, controller, props) | 5 (pom, app, controller, props×2) | docker-compose | **20** |
| **B** | 8 (+ JwtUtil, AuthController, JwtFilter) | 6 (+ JwtFilter, SecurityConfig) | 8 (+ JwtFilter, JwtPropagation, SecurityConfig) | docker-compose | **27** |
| **C** | 6 (+ KeycloakRoleConverter) | 7 (+ KeycloakRoleConverter, SecurityConfig) | 8 (+ KeycloakRoleConverter, TokenPropagation) | docker-compose, realm-export.json | **26** |
| **D** | 4 (pom, app, props×2) | 5 (pom, app, controller, props×2) | 6 (+ MtlsRestTemplateConfig) | docker-compose, generate-certs.sh | **19** |

### Dependencies Added

| Part | Gateway Dependencies | Service Dependencies |
|------|---------------------|---------------------|
| **A** | `spring-boot-starter-security` | None |
| **B** | `starter-security` + JJWT (api, impl, jackson) | `starter-security` + JJWT |
| **C** | `starter-oauth2-client` + `starter-oauth2-resource-server` | `starter-security` + `starter-oauth2-resource-server` |
| **D** | None (gateway SSL is config-only) | `httpclient5` (for mTLS RestTemplate) |

---

## Key Concepts Reference

### Spring Security 6 Key Classes

| Class | Module | Purpose |
|-------|--------|---------|
| `SecurityWebFilterChain` | WebFlux (Gateway) | Defines the reactive security filter chain |
| `SecurityFilterChain` | Servlet (Services) | Defines the servlet security filter chain |
| `@EnableWebFluxSecurity` | WebFlux | Enables reactive security |
| `@EnableWebSecurity` | Servlet | Enables servlet security |
| `ServerHttpSecurity` | WebFlux | Builder for reactive security rules |
| `HttpSecurity` | Servlet | Builder for servlet security rules |
| `ReactiveSecurityContextHolder` | WebFlux | Access security context in reactive code |
| `SecurityContextHolder` | Servlet | Access security context in servlet code |

### OAuth2 / OIDC Terminology

| Term | Meaning |
|------|---------|
| **Resource Owner** | The user (owns the data) |
| **Client** | The application requesting access (api-gateway, demo-app) |
| **Authorization Server** | Keycloak — issues tokens |
| **Resource Server** | greeting-service, time-service — validates tokens, serves data |
| **Access Token** | JWT that grants access to resources |
| **JWKS** | JSON Web Key Set — Keycloak's public keys endpoint |
| **TokenRelay** | Spring Cloud Gateway filter that forwards the token downstream |
| **Realm** | A Keycloak tenant — isolated set of users, roles, clients |

### TLS / mTLS Terminology

| Term | Meaning |
|------|---------|
| **CA** | Certificate Authority — the root of trust |
| **Keystore** | Contains a service's private key + certificate (identity) |
| **Truststore** | Contains certificates the service trusts (CA cert) |
| **CSR** | Certificate Signing Request — sent to the CA for signing |
| **SAN** | Subject Alternative Name — additional hostnames the cert is valid for |
| **client-auth=need** | Server requires the client to present a certificate |
| **PKCS12** | Keystore format (.p12 files) |

---

## Production at NatWest

In a real NatWest environment, you would combine multiple layers:

```
┌────────────────────────────────────────────────────────────┐
│                     Internet                                │
└────────────────────────┬───────────────────────────────────┘
                         │
                ┌────────▼────────┐
                │  WAF / CDN      │  ← Rate limiting, DDoS protection
                │  (CloudFlare)   │
                └────────┬────────┘
                         │
                ┌────────▼────────┐
                │  API Gateway    │  ← OAuth2 token validation (Part C)
                │  (Kong / APIM)  │     Rate limiting per client
                └────────┬────────┘
                         │ mTLS (Part D)
                ┌────────▼────────┐
                │  Service Mesh   │  ← mTLS between ALL services
                │  (Istio/Linkerd)│     Automatic cert rotation
                └────────┬────────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
         ┌────────┐ ┌────────┐ ┌────────┐
         │ Order  │ │ Payment│ │ Account│
         │Service │ │Service │ │Service │  ← Each validates JWT (Part C)
         └────────┘ └────────┘ └────────┘     Role-based access control
```

| Layer | Technology | What It Does |
|-------|-----------|-------------|
| Edge | WAF + CDN | DDoS protection, geo-blocking, rate limiting |
| Gateway | OAuth2 (Part C) | Token validation, route-level access control |
| Transport | mTLS (Part D) | Encrypt all traffic, verify service identity |
| Service | JWT validation | Per-endpoint role checks (`@PreAuthorize`) |
| Data | Encryption at rest | AES-256 for databases, key vaults for secrets |

**Key point:** You never use just one approach. Basic Auth is for dev tools. JWT is for stateless validation. OAuth2 is for user identity. mTLS is for service identity. They protect different things.

---

## Common Pitfalls

### 1. Forgetting to Propagate Tokens

```
Gateway validates token → OK
Gateway routes to time-service → token forwarded (Spring Cloud Gateway does this by default)
Time-service calls greeting-service → ❌ NO TOKEN! (RestTemplate doesn't forward headers)
```

**Fix:** Add a `ClientHttpRequestInterceptor` to copy the `Authorization` header.

### 2. Reactive vs Servlet Security

Spring Cloud Gateway uses **WebFlux** (reactive). Backend services use **Web MVC** (servlet). The security APIs are completely different:

| | Gateway (WebFlux) | Services (Servlet) |
|---|---|---|
| Annotation | `@EnableWebFluxSecurity` | `@EnableWebSecurity` |
| Config class | `ServerHttpSecurity` | `HttpSecurity` |
| Filter type | `WebFilter` | `OncePerRequestFilter` |
| Security context | `ReactiveSecurityContextHolder` | `SecurityContextHolder` |

Mixing them up causes silent failures.

### 3. Keycloak Issuer Mismatch in Docker

The token's `iss` claim is `http://localhost:8180/realms/demo` (how the client obtained it), but services running in Docker see Keycloak as `http://keycloak:8080`. Use `jwk-set-uri` instead of `issuer-uri`.

### 4. Spring Cloud Gateway Route List Binding

When overriding routes in `application-docker.properties`, you must include the **full route definition** (id + uri + predicates), not just the URI. Spring binds routes as a list — partial overrides produce empty predicates.

```properties
# WRONG (predicates become empty):
spring.cloud.gateway.routes[0].uri=http://greeting-service:9001

# CORRECT (full definition):
spring.cloud.gateway.routes[0].id=greeting-service
spring.cloud.gateway.routes[0].uri=http://greeting-service:9001
spring.cloud.gateway.routes[0].predicates[0]=Path=/greeting,/greeting/**
```

### 5. mTLS Health Checks

When `server.ssl.enabled=true`, the health check endpoint also requires HTTPS + client cert. Docker's `wget` health check fails. Use a separate management port:

```properties
management.server.port=9011
management.server.ssl.enabled=false
```

---

## Key Takeaways

1. **No single security mechanism is sufficient.** Basic Auth handles "who is this?", JWT adds "is this token valid?", OAuth2 adds "who issued this token?", mTLS adds "is this a trusted service?". Production systems layer multiple approaches.

2. **Stateless tokens scale.** JWT and OAuth2 tokens are self-contained — no server-side session, no database lookup per request. This is why they're the standard for microservices, unlike Basic Auth which requires validation against a user store on every request.

3. **Asymmetric keys eliminate shared secrets.** The jump from Part B (HMAC shared secret) to Part C (RSA public/private keys) is the most important architectural upgrade. Any service can verify a token, but only Keycloak can issue one. This fundamentally changes the trust model.

4. **Token propagation is the hardest problem.** Getting a token to the gateway is easy. Getting it to flow correctly through gateway → service A → service B requires explicit interceptors. Every service-to-service call must propagate the token, or the chain breaks.

5. **Transport security (mTLS) is orthogonal to application security.** mTLS protects the **connection** (who is calling?), while JWT protects the **request** (who is the user?). They answer different questions and should be used together in production.

6. **Spring Security 6 has two worlds.** The gateway (WebFlux/reactive) and backend services (Servlet) use completely different security APIs. `@EnableWebFluxSecurity` vs `@EnableWebSecurity`, `ServerHttpSecurity` vs `HttpSecurity`, `WebFilter` vs `OncePerRequestFilter`. Understanding which world you're in prevents hours of debugging.

---

## Port Reference

| Port | Service | Part(s) | Protocol |
|------|---------|---------|----------|
| 9000 | API Gateway | A, B, C, D | HTTP (A,B,C) / HTTPS (D) |
| 9001 | Greeting Service | A, B, C, D | HTTP (A,B,C) / HTTPS (D) |
| 9002 | Time Service | A, B, C, D | HTTP (A,B,C) / HTTPS (D) |
| 8180 | Keycloak | C only | HTTP |
| 9010 | Gateway Health | D only | HTTP |
| 9011 | Greeting Health | D only | HTTP |
| 9012 | Time Health | D only | HTTP |

---

> _End of Iteration 13 training module._
>
> _From wide open to locked down — four layers of API security that protect different things,
> at different layers, for different reasons._
