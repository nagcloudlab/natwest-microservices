# Iteration 15 Simple Demo: GraalVM Native Image

> **Goal**: Compare the same Spring Boot microservice running as a traditional JVM app vs a GraalVM Native Image — measuring startup time, memory usage, and image size.
>
> **Duration**: ~35 minutes
>
> **Pre-requisites**: Docker Desktop (with 6-8 GB RAM allocated), ports 9001 and 9002 available

---

## The Service

| Variant | Port | Image | Runtime |
|---------|------|-------|---------|
| **greeting-jvm** | 9001 | `greeting-jvm` | Eclipse Temurin JRE 17 |
| **greeting-native** | 9002 | `greeting-native` | GraalVM Native Binary (no JVM) |

Both run the **exact same Java source code** — a greeting-service with 4 classes. The only difference is how they are compiled and packaged.

### Endpoints (both variants)

| Endpoint | Purpose |
|----------|---------|
| `GET /greeting` | Returns greeting + hostname |
| `GET /info/runtime` | Returns mode (JVM/Native), startup time, memory, VM info |
| `GET /actuator/health` | Health check |

---

## Opening (2 min)

**Story to tell:**

> "You're running 40 microservices in production. Each one uses Spring Boot on a JVM — around 250 MB of memory at idle. That's **40 × 250 MB = 10 GB of RAM** just sitting there, doing nothing, waiting for requests."
>
> "And every time you deploy, each service takes 2-3 seconds to start. In a Kubernetes rolling update, that's 2-3 seconds where the old pod is gone but the new one isn't ready yet. Multiply that across 40 services..."
>
> "What if the same Java code could start in 50 milliseconds and use 40 MB of memory? That's what GraalVM Native Image promises. Let's see if it delivers."

---

## Act 1: JVM Baseline (8 min)

### Step 1 — Build the JVM image

```bash
cd time-greet-services/iteration-15-graalvm-native-image/greeting-service
docker build -f Dockerfile.jvm -t greeting-jvm .
```

> **Point out**: This is the standard multi-stage build pattern from iteration 7 — Maven builds the JAR, then we copy it onto a JRE base image.

### Step 2 — Check the image size

```bash
docker images greeting-jvm
```

Expected output:
```
REPOSITORY     TAG       IMAGE ID       CREATED         SIZE
greeting-jvm   latest    ...            ...             ~300MB
```

> "~300 MB for a service that returns a greeting. Most of that is the JRE."

### Step 3 — Run and test

```bash
docker run -d --name jvm-test -p 9001:9001 greeting-jvm
```

Wait a moment, then:

```bash
curl localhost:9001/info/runtime | jq
```

Expected response:
```json
{
  "mode": "JVM",
  "startupTimeMs": 2100,
  "memoryUsedMB": 85,
  "maxMemoryMB": 4002,
  "processors": 8,
  "vmName": "OpenJDK 64-Bit Server VM",
  "vmVersion": "17.0.x+x"
}
```

> **Key numbers to note:**
> - `mode: "JVM"` — running on a traditional JVM
> - `startupTimeMs: ~2000` — about 2 seconds to start
> - `memoryUsedMB` — heap memory reported by the JVM

```bash
docker stats --no-stream jvm-test
```

> "Check the MEM USAGE column — that's the actual RSS memory the container is using. Typically ~200 MB for this JVM service."

### Step 4 — Clean up

```bash
docker stop jvm-test && docker rm jvm-test
```

### Summary

> "Our baseline: ~300 MB image, ~2 second startup, ~200 MB RSS memory. This is what most Spring Boot services look like. Now let's see what GraalVM can do."

---

## Act 2: Building the Native Image (10 min)

### What Is GraalVM Native Image?

> "GraalVM Native Image is an ahead-of-time (AOT) compiler. Instead of shipping a JAR and running it on a JVM at runtime, we compile the entire application — including Spring Boot, Tomcat, all dependencies — into a single standalone binary. No JVM required."
>
> "Think of it like the difference between Python (interpreted) and C (compiled). Same logic, completely different execution model."

### The pom.xml Change

Open `greeting-service/pom.xml` and show the native profile:

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

> **Explain**: "That's it. Three lines of meaningful config. Spring Boot 3.x's parent BOM already manages the plugin version. When we activate the `native` profile with `-Pnative`, Maven uses GraalVM's compiler instead of `javac` + JAR packaging."

### The Native Dockerfile

Open `greeting-service/Dockerfile.native` and walk through each stage:

```dockerfile
# Stage 1: Copy Maven from the official Maven image
FROM maven:3.9-eclipse-temurin-17 AS maven-source

# Stage 2: Build the native image using GraalVM
FROM ghcr.io/graalvm/native-image-community:17 AS build
WORKDIR /app
COPY --from=maven-source /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"

COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn -Pnative native:compile -DskipTests -B

# Stage 3: Minimal runtime image — no JVM needed
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends libz-dev && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/greeting-service /app/greeting-service
EXPOSE 9001
ENTRYPOINT ["/app/greeting-service"]
```

> **Key differences from the JVM Dockerfile:**
> - **Builder image**: `ghcr.io/graalvm/native-image-community:17` instead of `maven:...` — this image has the GraalVM native-image compiler
> - **Maven trick**: GraalVM's image doesn't include Maven, so we copy it from the Maven image (stage 1)
> - **Build command**: `mvn -Pnative native:compile` — this runs the AOT compiler (takes 3-7 minutes!)
> - **Runtime image**: `debian:bookworm-slim` — just a minimal Linux. No JRE, no JVM. The binary runs directly on the OS
> - **Why not Alpine?**: GraalVM native images are compiled against glibc. Alpine uses musl. Using Alpine would require a static build or musl compatibility layer

### Step 1 — Build the native image

```bash
docker build -f Dockerfile.native -t greeting-native .
```

> "This will take 3-7 minutes. The AOT compiler is analyzing every class, every method call, every reflection path in your application. It's doing at build time what the JVM normally does at runtime."
>
> "Watch the memory usage of Docker Desktop — the compiler needs 4-6 GB of RAM. This is the main trade-off: longer build time for faster runtime."

### Step 2 — Check the image size

```bash
docker images greeting-native
```

Expected output:
```
REPOSITORY         TAG       IMAGE ID       CREATED         SIZE
greeting-native    latest    ...            ...             ~90MB
```

> "~90 MB vs ~300 MB. The native binary is about 70 MB, sitting on a 25 MB Debian slim base. No JRE needed."

---

## Act 3: Running Native (5 min)

### Step 1 — Run and test

```bash
docker run -d --name native-test -p 9002:9001 greeting-native
```

```bash
curl localhost:9002/info/runtime | jq
```

Expected response:
```json
{
  "mode": "Native",
  "startupTimeMs": 48,
  "memoryUsedMB": 18,
  "maxMemoryMB": 256,
  "processors": 8,
  "vmName": "Substrate VM",
  "vmVersion": "GraalVM CE 17.x"
}
```

> **Key moment**: "Look at the numbers:
> - `mode: "Native"` — this is NOT running on a JVM
> - `startupTimeMs: 48` — **48 milliseconds!** That's 40x faster than the JVM's 2 seconds
> - `vmName: "Substrate VM"` — GraalVM's minimal runtime, not the HotSpot JVM
>
> The same Java code. The same Spring Boot framework. Completely different performance characteristics."

### Step 2 — Check container memory

```bash
docker stats --no-stream native-test
```

> "MEM USAGE: ~40 MB vs ~200 MB for the JVM version. That's 5x less memory for the same service."

### Step 3 — Verify identical behavior

```bash
curl localhost:9002/greeting | jq
```

```json
{
  "message": "Hello from Greeting Service!",
  "host": "a1b2c3d4e5f6"
}
```

> "Identical response. The application behavior hasn't changed at all."

### Step 4 — Clean up

```bash
docker stop native-test && docker rm native-test
```

---

## Act 4: Side-by-Side Comparison (5 min)

### Step 1 — Run both with Docker Compose

```bash
cd time-greet-services/iteration-15-graalvm-native-image
docker compose up -d
```

> "This starts both variants: JVM on port 9001, Native on port 9002. Notice the health check config — the native version has `start_period: 5s` vs `30s` for JVM. The native version is healthy almost immediately."

### Step 2 — Run the comparison script

```bash
./compare.sh
```

> "The script builds both images, starts them, waits for health, then collects metrics from both `/info/runtime` endpoints and `docker stats`."

### Step 3 — The numbers

| Metric | JVM | Native | Improvement |
|--------|-----|--------|-------------|
| Startup time | ~2,000 ms | ~50 ms | **40x faster** |
| Memory (RSS) | ~200 MB | ~40 MB | **5x less** |
| Docker image | ~300 MB | ~90 MB | **3x smaller** |
| Build time | ~30 sec | ~5 min | 10x slower |

> "Every metric improves except build time. This is the fundamental trade-off: you pay more at build time to get better runtime characteristics."

### How Does Native Mode Get Detected?

```java
boolean isNative = System.getProperty("org.graalvm.nativeimage.imagecode") != null;
```

> "We don't import any GraalVM SDK. This system property is automatically set when running as a native image. Zero additional dependencies."

---

## Act 5: Real-World Challenges (5 min)

> "If native images are this much better, why isn't everyone using them? Because there are real trade-offs."

### 1. Reflection Must Be Known at Build Time

> "The JVM discovers classes at runtime using reflection. Native Image needs to know ALL classes at build time — the 'closed-world assumption'. Spring Boot 3.x handles most of this automatically with its AOT engine, but custom reflection still needs configuration."

### 2. No Dynamic Class Loading

> "Libraries that generate bytecode at runtime (like some ORMs, mocking frameworks, or dynamic proxies) won't work out of the box. You can't load a class that didn't exist at compile time."

### 3. Library Compatibility

> "Not all Java libraries support native image yet. Spring Boot 3.x has excellent support, but check your dependencies. Libraries using JNI, unsafe memory access, or custom classloaders may need hints or alternatives."

### 4. Debugging Is Harder

> "No JMX, no JFR (Java Flight Recorder), limited profiling tools. When your native service has a production issue, you have fewer diagnostic tools than with a JVM."

### 5. Build Resources

> "We saw it: 4-6 GB RAM, 3-7 minutes per build. In a CI/CD pipeline with 40 services, that's significant. Some teams build native images only for release, not for every commit."

### 6. When to Use Which?

| Use Case | Recommendation | Why |
|----------|---------------|-----|
| Serverless (AWS Lambda, Azure Functions) | **Native** | Cold start time matters most |
| Kubernetes with autoscaling | **Native** | Fast scale-up, low memory per pod |
| Long-running, high-throughput | **JVM** | JIT compiler optimizes hot paths over time |
| Development / debugging | **JVM** | Fast builds, full debugging tools |
| Libraries with heavy reflection | **JVM** | Easier compatibility |

> "The JVM's JIT compiler actually produces FASTER code for long-running services because it optimizes based on actual runtime behavior. Native Image wins on startup and memory, but the JVM wins on peak throughput for sustained workloads."

---

## Bridge to FTGO (2 min)

> "In our FTGO application, which services would benefit most from native image?"
>
> **Good candidates for Native:**
> - **API Gateway** — needs fast startup for scaling, mostly routing (low reflection)
> - **Notification Service** — stateless, event-driven, scales up/down frequently
> - **Consumer Service** — simple CRUD, could benefit from lower memory footprint
>
> **Better as JVM:**
> - **Order Service** — complex saga orchestration, heavy reflection, needs debugging tools
> - **Kitchen Service** — long-running process, benefits from JIT optimization
>
> "In practice, most teams start by converting their simplest, most stateless services to native, then expand as library support improves."

---

## Cleanup

```bash
# Stop both services
docker compose down

# Remove images (optional, frees disk space)
docker rmi greeting-jvm greeting-native
```

---

## Summary: What We Learned

| Metric | JVM | Native Image | Trade-off |
|--------|-----|-------------|-----------|
| Startup time | ~2 sec | ~0.05 sec | Build takes 10x longer |
| Memory (RSS) | ~200 MB | ~40 MB | Less diagnostic tooling |
| Image size | ~300 MB | ~90 MB | Closed-world assumption |
| Peak throughput | Higher (JIT) | Lower (AOT) | JVM wins for long-running |
| Build complexity | Simple | Complex | Reflection/library compat |

---

## Discussion Questions

1. **Cost Savings**: "If each of your 40 services uses 200 MB less memory, how much could you save on cloud infrastructure per year?"

2. **CI/CD Impact**: "If native builds take 5 minutes each and you have 40 services, how would you structure your CI/CD pipeline?" _(Build native only on release, JVM for feature branches)_

3. **Hybrid Strategy**: "Could you run JVM in development and native in production? What risks does that introduce?" _(Different behavior is possible — test thoroughly)_

4. **Serverless**: "AWS Lambda charges per millisecond of execution and per MB of memory. How would native image change your Lambda costs?"

5. **Future**: "Spring Boot 3.x made native support much easier than Spring Boot 2.x. What does that trend tell you about where the Java ecosystem is heading?"

---

## Quick Reference

```bash
# Build JVM image
docker build -f greeting-service/Dockerfile.jvm -t greeting-jvm greeting-service/

# Build Native image (3-7 min, needs 6GB+ Docker RAM)
docker build -f greeting-service/Dockerfile.native -t greeting-native greeting-service/

# Run JVM version
docker run -d -p 9001:9001 greeting-jvm

# Run Native version
docker run -d -p 9002:9001 greeting-native

# Compare endpoints
curl localhost:9001/info/runtime | jq
curl localhost:9002/info/runtime | jq

# Run both with Docker Compose
docker compose up -d

# Automated comparison
./compare.sh

# Stop everything
docker compose down
```
