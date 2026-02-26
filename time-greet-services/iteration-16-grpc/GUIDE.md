# Iteration 16 Simple Demo: gRPC Between Services

> **Goal**: Compare REST and gRPC for internal service-to-service communication — same services, two transports, measurable difference.
>
> **Duration**: ~35 minutes
>
> **Pre-requisites**: Docker Desktop installed, completed iteration 7 (Docker basics)

---

## The Services

| Service | Port | Endpoints | Purpose |
|---------|------|-----------|---------|
| **greeting-service** | 9001 (REST), 9090 (gRPC) | `GET /greeting`, `rpc GetGreeting` | Returns greeting via REST **and** gRPC |
| **time-service** | 9002 | `GET /time`, `/time/with-greeting`, `/time/with-greeting/grpc`, `/time/with-greeting/compare` | Calls greeting-service via both transports |

---

## Opening (2 min)

**Story to tell:**

> "REST is the lingua franca of web services. Every team knows it, every tool supports it, every browser can call it. But here's the question: **is it the fastest way for two backend services to talk to each other?**"
>
> "Think about it — when time-service calls greeting-service, there's no browser involved. No human reading JSON. It's one machine talking to another. Do we really need human-readable text format for that?"
>
> "Today we'll add a second communication channel — gRPC — alongside REST, and measure the difference."

---

## Act 1: REST Baseline (5 min)

### Step 1 — Start the services

```bash
cd time-greet-services/iteration-16-grpc
docker compose up --build
```

Wait for both services to start (greeting-service health check passes, then time-service starts).

### Step 2 — Test the REST endpoint

```bash
curl localhost:9001/greeting | jq
```

```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

### Step 3 — REST-to-REST call (our baseline)

```bash
curl localhost:9002/time/with-greeting | jq
```

```json
{
  "currentTime": "2025-01-15 14:30:25",
  "host": "f7e8d9c0b1a2",
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "a1b2c3d4e5f6"
  }
}
```

> **Point out**: "This is what we've been doing since iteration 7. Time-service calls greeting-service over REST — HTTP/1.1, JSON serialization, text-based. It works perfectly. But can we do better for internal calls?"

---

## Act 2: gRPC Alternative (5 min)

### Step 4 — Same result via gRPC

```bash
curl localhost:9002/time/with-greeting/grpc | jq
```

```json
{
  "currentTime": "2025-01-15 14:30:30",
  "host": "f7e8d9c0b1a2",
  "greeting": {
    "message": "Hello from Greeting Service!",
    "host": "a1b2c3d4e5f6"
  },
  "transport": "gRPC"
}
```

> **Point out**: "Same data, same result — but the internal call from time-service to greeting-service used gRPC instead of REST. The external API (what we curl) is still REST. Only the **internal** communication changed."

---

## Act 3: The Comparison — Key Demo (5 min)

### Step 5 — Side-by-side latency comparison

```bash
curl localhost:9002/time/with-greeting/compare | jq
```

```json
{
  "currentTime": "2025-01-15 14:30:35",
  "host": "f7e8d9c0b1a2",
  "rest": {
    "transport": "REST (HTTP/1.1 + JSON)",
    "latencyMs": 32,
    "greeting": {
      "message": "Hello from Greeting Service!",
      "host": "a1b2c3d4e5f6"
    }
  },
  "grpc": {
    "transport": "gRPC (HTTP/2 + Protobuf)",
    "latencyMs": 8,
    "greeting": {
      "message": "Hello from Greeting Service!",
      "host": "a1b2c3d4e5f6"
    }
  }
}
```

> **Key moment**: "Look at the latency numbers. Both calls go to the same service, return the same data. But gRPC is typically **2-4x faster**. Why?"

Run it a few more times to see the pattern:

```bash
# Run several times to see consistent difference
for i in 1 2 3 4 5; do
  curl -s localhost:9002/time/with-greeting/compare | jq '{rest_ms: .rest.latencyMs, grpc_ms: .grpc.latencyMs}'
done
```

> **Note**: The first gRPC call may be slower (channel setup). Subsequent calls benefit from HTTP/2 connection reuse.

---

## Act 4: Why Is gRPC Faster? (8 min)

### HTTP/2 Binary vs HTTP/1.1 Text

| Aspect | REST | gRPC |
|--------|------|------|
| **Protocol** | HTTP/1.1 | HTTP/2 |
| **Serialization** | JSON (text) | Protobuf (binary) |
| **Message size** | Larger (keys repeated, human-readable) | Smaller (field numbers, compact encoding) |
| **Connection** | New connection per request (or keep-alive) | Multiplexed — many calls over one connection |
| **Contract** | OpenAPI/Swagger (optional) | `.proto` file (required, strongly typed) |
| **Browser support** | Native | Requires grpc-web proxy |
| **Best for** | External APIs, browser clients | Internal service-to-service, high-throughput |

### Show the .proto file

Open `proto/greeting.proto`:

```protobuf
syntax = "proto3";

package greeting;

option java_package = "com.demo.greeting.grpc.proto";
option java_outer_classname = "GreetingProto";

service GreetingService {
    rpc GetGreeting (GreetingRequest) returns (GreetingResponse);
}

message GreetingRequest {}

message GreetingResponse {
    string message = 1;
    string host = 2;
}
```

> **Explain**: "This `.proto` file IS the API contract. Both the server and client generate code from it. If I add a field here, both sides know about it at compile time. No more 'I changed the JSON field name and broke the other service at 2am.'"

---

## Act 5: Code Walkthrough (10 min)

### The Server Side (greeting-service)

**`GreetingGrpcService.java`** — The gRPC endpoint:

```java
@GrpcService
public class GreetingGrpcService extends GreetingServiceGrpc.GreetingServiceImplBase {

    @Override
    public void getGreeting(GreetingProto.GreetingRequest request,
                            StreamObserver<GreetingProto.GreetingResponse> responseObserver) {
        GreetingProto.GreetingResponse response = GreetingProto.GreetingResponse.newBuilder()
                .setMessage("Hello from Greeting Service!")
                .setHost(InetAddress.getLocalHost().getHostName())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

> **Point out**:
> - `@GrpcService` — like `@RestController` but for gRPC
> - `GreetingServiceImplBase` — generated from the `.proto` file
> - `StreamObserver` — gRPC's response pattern (supports streaming too)
> - The greeting-service now exposes the **same data** over two protocols: REST on port 9001, gRPC on port 9090

### The Client Side (time-service)

**`GrpcGreetingClient.java`** — The gRPC client:

```java
@Component
public class GrpcGreetingClient {

    @GrpcClient("greeting-service")
    private GreetingServiceGrpc.GreetingServiceBlockingStub greetingStub;

    public Map<String, String> getGreeting() {
        GreetingProto.GreetingResponse response = greetingStub.getGreeting(
                GreetingProto.GreetingRequest.newBuilder().build()
        );
        // Convert to Map for JSON response
        Map<String, String> result = new LinkedHashMap<>();
        result.put("message", response.getMessage());
        result.put("host", response.getHost());
        return result;
    }
}
```

> **Point out**:
> - `@GrpcClient("greeting-service")` — injects a typed stub (like RestTemplate but type-safe)
> - `BlockingStub` — synchronous call (also available: async, future stubs)
> - No URL building, no JSON parsing — it's a method call with typed request/response

### Configuration

**`application-docker.properties`** (time-service):
```properties
grpc.client.greeting-service.address=static://greeting-service:9090
grpc.client.greeting-service.negotiationType=plaintext
```

> **Explain**: `static://` means "connect to this address directly" (no service discovery). `plaintext` means no TLS (fine for Docker network; in production you'd use TLS).

### The Build — How .proto Becomes Java

Show `pom.xml` (the protobuf-maven-plugin section):

> "The `protobuf-maven-plugin` runs during the build. It takes `greeting.proto`, runs the `protoc` compiler, and generates Java classes: `GreetingProto` (messages), `GreetingServiceGrpc` (stubs). Both services share the same `.proto` file — that's the contract."

---

## Summary

| Aspect | REST | gRPC |
|--------|------|------|
| **Protocol** | HTTP/1.1 | HTTP/2 |
| **Serialization** | JSON (text, ~100 bytes) | Protobuf (binary, ~20 bytes) |
| **API contract** | OpenAPI (optional) | `.proto` file (required) |
| **Type safety** | Runtime (JSON parsing can fail) | Compile-time (generated code) |
| **Browser support** | Native | Requires proxy |
| **Streaming** | SSE / WebSocket (separate) | Built-in (unary, server, client, bidirectional) |
| **Best for** | External APIs, browser clients | Internal service-to-service, high throughput |
| **Latency** | Higher (text parsing, larger payloads) | Lower (binary, multiplexed) |

### When to Use Which?

- **REST**: External APIs, browser/mobile clients, public APIs, simple CRUD
- **gRPC**: Service-to-service, high-throughput internal calls, streaming data, polyglot systems (proto generates code for 10+ languages)
- **Both**: Many systems use REST externally + gRPC internally — exactly what we built today

---

## Bridge to FTGO

> "In the FTGO application, all services communicate over REST. That works fine for our demo scale. But imagine a production system processing thousands of orders per second — Order Service calling Kitchen Service, Delivery Service, Accounting Service. Each call adds latency."
>
> "With gRPC for those internal calls, you'd get faster serialization, type-safe contracts, and HTTP/2 multiplexing. REST stays for the consumer-facing API. This is exactly the pattern Netflix, Google, and most large-scale microservice systems use."

---

## Cleanup

```bash
docker compose down
```

---

## Discussion Questions

1. **Contract evolution**: "What happens if we add a field to the proto file? Do all services need to redeploy at once?" _(No — protobuf is backward compatible. New fields are optional by default.)_

2. **Debugging**: "How do you debug gRPC calls? You can't just use `curl`." _(Tools like `grpcurl`, `grpcui`, or Postman's gRPC support. Also logging/tracing.)_

3. **Streaming**: "We used unary RPC (one request, one response). What if you wanted real-time order status updates?" _(Server streaming RPC — server sends multiple responses for one request.)_

4. **Language mixing**: "What if your team writes one service in Go and another in Java?" _(The `.proto` file generates code for both languages. That's a major gRPC advantage.)_

5. **When NOT to use gRPC**: "Would you use gRPC for a public REST API that mobile apps call?" _(Generally no — browsers don't natively support gRPC. Use REST/GraphQL externally, gRPC internally.)_
