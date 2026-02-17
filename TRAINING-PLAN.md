# NatWest Microservices Training — Complete Plan

> Trainer's master plan for the full training program.
> If we disconnect, resume from the **Current Status** section below.

---

## Current Status

- **Last completed**: Part 2, Phase 0 — FTGO Monolith built and verified
- **Next up**: Part 2, Phase 1 — Show the Pain (demonstrate monolith problems)
- **Session-1**: Covered Spring Boot basics (design-patterns, transfer-service demos)
- **FTGO Monolith**: Built, compiled, tested — all 6 modules + external mocks working

---

## Training Overview

```
Theory (Done) → Monolith Demo → Microservices Migration → Containers → K8s → AWS
                     ↑                   ↑                    ↑          ↑       ↑
                   "WHY"           "HOW to split"        "HOW to      "HOW to   "HOW to run
                                    the code"            package"   orchestrate" in production"
```

**Approach**: Demo-driven. Build a monolith first. Feel the pain. Then migrate incrementally — one pattern at a time. Each phase introduces exactly one new concept.

**Application**: FTGO (Food To Go) — online food delivery platform.

**Tech Stack**:
- Language: Java 17+
- Framework: Spring Boot 3.x, Spring Cloud
- Database: PostgreSQL
- Messaging: RabbitMQ / Kafka
- API Gateway: Spring Cloud Gateway
- Discovery: Eureka
- Config: Spring Cloud Config Server
- Resilience: Resilience4j
- Tracing: Zipkin
- Logging: ELK (Elasticsearch, Logstash, Kibana)
- Containers: Docker, Docker Compose
- Orchestration: Kubernetes (minikube locally, EKS on AWS)
- Cloud: AWS (EKS, RDS, ECR, ALB, CloudWatch, X-Ray)
- CI/CD: GitHub Actions or AWS CodePipeline

---

## Part 1: Theory (COMPLETED)

| Module | Topic | Status |
|--------|-------|--------|
| Module 1 | THE FTGO MONOLITH — Appreciating What Works | DONE |
| Module 2 | THE SCALE CUBE & MICROSERVICES | DONE |
| Module 3 | DOMAIN-DRIVEN DECOMPOSITION | DONE |

**Materials**: All PDFs in `docs/` folder.

---

## Part 2: Demos — Architecture Migration (Monolith to Microservices)

### Phase 0: Build FTGO Monolith
- **Goal**: Build a working monolithic FTGO app with all modules in one codebase
- **What to build**:
  - Single Spring Boot app
  - Single PostgreSQL database
  - Modules as packages: order, kitchen, delivery, restaurant, accounting, notification, user
  - REST APIs for: place order, accept ticket, assign courier, process payment, send notification
  - Single `@Transactional` flow: create order → authorize payment → create kitchen ticket
- **Patterns**: Monolithic Architecture
- **Key teaching point**: Show how simple it is — one build, one deploy, one DB, ACID transactions
- **Folder**: `ftgo-monolith/`
- **Status**: DONE

### Phase 1: Show the Pain
- **Goal**: Demonstrate why the monolith becomes problematic
- **What to demo**:
  - One bug in notification crashes the entire app (simulate OutOfMemoryError)
  - Change one line → full rebuild needed
  - Can't scale kitchen independently (all-or-nothing scaling)
  - All teams share one codebase, one pipeline
- **Key teaching point**: Let trainees FEEL the pain before showing the solution
- **Status**: NOT STARTED

### Phase 2: Extract Notification Service (First Extraction)
- **Goal**: Extract the lowest-risk service using Strangler Fig pattern
- **What to build**:
  - New standalone Spring Boot app: `notification-service`
  - Async messaging: monolith publishes events → notification-service consumes
  - RabbitMQ or Kafka as message broker
  - Remove notification code from monolith
- **Patterns**: Strangler Fig, Async Messaging, Database per Service
- **Key teaching point**: Incremental migration — old and new coexist
- **Folder**: `notification-service/`
- **Status**: NOT STARTED

### Phase 3: Extract Restaurant Service
- **Goal**: Extract a simple CRUD service with clear boundaries
- **What to build**:
  - New Spring Boot app: `restaurant-service`
  - Own PostgreSQL database (restaurant, menu tables)
  - REST API for restaurant CRUD
  - Monolith calls restaurant-service instead of local module
- **Patterns**: Decompose by Business Capability, Database per Service, Service per Team
- **Key teaching point**: Each service owns its data — no shared DB
- **Folder**: `restaurant-service/`
- **Status**: NOT STARTED

### Phase 4: Introduce API Gateway & Service Discovery
- **Goal**: Centralize client access and enable dynamic service lookup
- **What to build**:
  - Spring Cloud Gateway as single entry point
  - Eureka Server for service registration/discovery
  - All existing services register with Eureka
  - Gateway routes requests to services via Eureka
- **Patterns**: API Gateway, Service Discovery, Edge Server
- **Key teaching point**: Clients talk to ONE URL. Gateway routes to the right service.
- **Folders**: `api-gateway/`, `eureka-server/`
- **Status**: NOT STARTED

### Phase 5: Extract Delivery Service
- **Goal**: Extract a service with different tech needs (real-time GPS tracking)
- **What to build**:
  - New Spring Boot app: `delivery-service`
  - Own database
  - Anti-Corruption Layer to translate between monolith's legacy model and new model
  - Possibly different DB choice (MongoDB for flexible courier/GPS data)
- **Patterns**: Anti-Corruption Layer, Polyglot Persistence
- **Key teaching point**: Each service can choose the best tech for its job
- **Folder**: `delivery-service/`
- **Status**: NOT STARTED

### Phase 6: Extract Kitchen Service
- **Goal**: Extract a service that requires coordination with Order (saga)
- **What to build**:
  - New Spring Boot app: `kitchen-service`
  - Saga pattern: Order → Kitchen → Accounting coordination
  - Event-driven choreography via message broker
  - Compensating transactions (if kitchen rejects → cancel order)
- **Patterns**: Saga Pattern, Event-Driven Choreography, Eventual Consistency
- **Key teaching point**: No more ACID across services. Sagas replace distributed transactions.
- **Folder**: `kitchen-service/`
- **Status**: NOT STARTED

### Phase 7: Extract Accounting Service
- **Goal**: Extract financial service with careful data migration
- **What to build**:
  - New Spring Boot app: `accounting-service`
  - Own PostgreSQL database (financial data needs ACID)
  - ACL to wrap external payment provider (Stripe/Razorpay)
  - CQRS pattern if needed for reporting
- **Patterns**: CQRS, Anti-Corruption Layer, Database per Service
- **Key teaching point**: Financial data requires extra care — ACID locally, eventual consistency across services
- **Folder**: `accounting-service/`
- **Status**: NOT STARTED

### Phase 8: Extract Order Service (Last)
- **Goal**: Extract the most complex, central service
- **What to build**:
  - New Spring Boot app: `order-service`
  - Hub of all interactions — orchestrates sagas
  - Communicates with all other services via events/APIs
  - Monolith is now EMPTY — fully decomposed
- **Patterns**: Orchestration vs Choreography (compare both)
- **Key teaching point**: Extract the hardest service last — when team has experience
- **Folder**: `order-service/`
- **Status**: NOT STARTED

### Phase 9: Cross-Cutting Concerns
- **Goal**: Add production-grade resilience and observability
- **What to build**:
  - Circuit Breaker (Resilience4j) — handle downstream failures gracefully
  - Centralized Configuration (Spring Cloud Config Server)
  - Distributed Tracing (Zipkin) — trace a request across all services
  - Centralized Logging (ELK stack) — aggregate logs from all services
  - Health checks and metrics (Spring Boot Actuator)
- **Patterns**: Circuit Breaker, Central Config, Distributed Tracing, Centralized Log Analysis, Control Loop
- **Folders**: `config-server/`, update all services
- **Status**: NOT STARTED

---

## Part 3: Demos — Containerization

### Phase 10: Dockerize Each Service
- **Goal**: Package each service as a Docker container
- **What to build**:
  - Dockerfile for each service (multi-stage builds)
  - `.dockerignore` files
  - Image optimization (small base images, layer caching)
  - Build and run individual containers
- **Concepts**: Dockerfile, multi-stage builds, image layers, base images
- **Key teaching point**: "Works on my machine" → works EVERYWHERE
- **Status**: NOT STARTED

### Phase 11: Docker Compose — Run Full FTGO Locally
- **Goal**: One command to start the entire FTGO ecosystem
- **What to build**:
  - `docker-compose.yml` with all services, databases, message broker, gateway, eureka
  - Service networking (Docker networks)
  - Volumes for data persistence
  - Environment-specific configuration via `.env`
- **Concepts**: Docker Compose, service networking, volumes, environment config
- **Key teaching point**: `docker-compose up` starts everything. Pain of 8 terminals → gone.
- **Status**: NOT STARTED

### Phase 12: Container Registry
- **Goal**: Push images to a registry for sharing/deployment
- **What to build**:
  - Push images to Docker Hub (or AWS ECR — preview for Part 5)
  - Image versioning and tagging strategy (latest, semver, git-sha)
  - Pull and run from registry
- **Concepts**: Image registry, versioning, tagging
- **Status**: NOT STARTED

---

## Part 4: Demos — Kubernetes

### Phase 13: K8s Fundamentals — Deploy One Service
- **Goal**: Understand K8s basics by deploying one service to local K8s
- **What to build**:
  - Install minikube or kind (local K8s cluster)
  - Deploy order-service: Pod, Deployment, Service (ClusterIP)
  - Deploy PostgreSQL as a Pod
  - `kubectl` commands: apply, get, describe, logs, exec
- **Concepts**: Pods, Deployments, Services, Namespaces, kubectl
- **Key teaching point**: K8s manages your containers — restarts, networking, scaling
- **Status**: NOT STARTED

### Phase 14: Deploy Full FTGO to K8s
- **Goal**: Run the entire FTGO platform on Kubernetes
- **What to build**:
  - K8s manifests (YAML) for all services
  - ConfigMaps for application configuration
  - Secrets for passwords, API keys
  - Ingress Controller (NGINX) for external access
  - Namespaces for logical separation
- **Concepts**: ConfigMaps, Secrets, Ingress, Namespaces
- **Folder**: `k8s/` (all manifests)
- **Status**: NOT STARTED

### Phase 15: Scaling & Self-Healing
- **Goal**: Demo the power of K8s — auto-scaling and recovery
- **What to demo**:
  - Scale Kitchen Service to 10 replicas (`kubectl scale` or HPA)
  - Kill a pod → K8s restarts it automatically
  - Horizontal Pod Autoscaler (HPA) based on CPU/memory
  - Liveness probes (restart unhealthy pods)
  - Readiness probes (don't send traffic until ready)
- **Concepts**: HPA, ReplicaSets, Liveness/Readiness probes, self-healing
- **Key teaching point**: "Saturday night" scenario — Kitchen auto-scales, others stay small
- **Status**: NOT STARTED

### Phase 16: Helm Charts (Optional)
- **Goal**: Package FTGO for repeatable, configurable deployment
- **What to build**:
  - Helm chart for FTGO
  - `values.yaml` per environment (dev, staging, prod)
  - `helm install ftgo ./ftgo-chart`
  - Upgrade and rollback demos
- **Concepts**: Helm templating, values files, releases, rollback
- **Folder**: `helm/ftgo-chart/`
- **Status**: NOT STARTED

### Phase 17: Service Mesh (Optional/Advanced)
- **Goal**: Introduce service mesh for advanced traffic management
- **What to demo**:
  - Install Istio or Linkerd
  - mTLS between services (zero-code security)
  - Traffic splitting (canary deployments)
  - Observability (built-in metrics, tracing)
- **Concepts**: Service mesh, sidecar proxy, mTLS, traffic management
- **Status**: NOT STARTED

---

## Part 5: Demos — AWS Cloud

### Phase 18: EKS — Deploy FTGO to AWS
- **Goal**: Move from local K8s to AWS managed Kubernetes
- **What to build**:
  - Create EKS cluster using `eksctl`
  - Node groups (managed EC2 instances)
  - IAM roles for service accounts (IRSA)
  - Deploy FTGO to EKS (same manifests, different context)
- **Concepts**: EKS, eksctl, node groups, IRSA
- **Key teaching point**: Same K8s manifests, production-grade infrastructure
- **Status**: NOT STARTED

### Phase 19: AWS Managed Services — Replace Self-Hosted Infra
- **Goal**: Use AWS managed services instead of running your own
- **What to migrate**:
  - PostgreSQL Pods → **Amazon RDS** (managed PostgreSQL)
  - Kafka/RabbitMQ Pods → **Amazon MSK** or **SQS/SNS**
  - Redis (if used) → **Amazon ElastiCache**
- **Concepts**: RDS, MSK, SQS/SNS, ElastiCache
- **Key teaching point**: Don't manage databases in K8s — use managed services in production
- **Status**: NOT STARTED

### Phase 20: ECR — Private Container Registry on AWS
- **Goal**: Store Docker images privately on AWS
- **What to build**:
  - Create ECR repositories for each service
  - Push/pull images from ECR
  - Lifecycle policies (auto-delete old images)
  - EKS pulls images from ECR (IAM-based auth)
- **Concepts**: ECR, lifecycle policies, IAM authentication
- **Status**: NOT STARTED

### Phase 21: ALB Ingress — Expose FTGO to the Internet
- **Goal**: Production-grade external access with HTTPS
- **What to build**:
  - AWS Load Balancer Controller
  - Application Load Balancer (ALB) via Ingress resource
  - Route53 for DNS (ftgo.example.com)
  - ACM for TLS/SSL certificates
- **Concepts**: ALB Ingress Controller, Route53, ACM, HTTPS
- **Status**: NOT STARTED

### Phase 22: Observability on AWS
- **Goal**: Production monitoring and debugging
- **What to build**:
  - **CloudWatch Logs** — aggregate logs from all services
  - **AWS X-Ray** — distributed tracing across services
  - **CloudWatch Metrics & Alarms** — CPU, memory, error rate alerts
  - **CloudWatch Dashboards** — single pane of glass
- **Concepts**: CloudWatch, X-Ray, alarms, dashboards
- **Status**: NOT STARTED

### Phase 23: CI/CD Pipeline
- **Goal**: Automated build → test → deploy pipeline
- **What to build**:
  - GitHub Actions (or AWS CodePipeline):
    - On push: build → test → Docker build → push to ECR → deploy to EKS
  - Per-service pipelines (independent deployment)
  - Environment promotion: dev → staging → prod
- **Concepts**: CI/CD, GitOps, environment promotion, independent pipelines
- **Status**: NOT STARTED

### Phase 24: Production Readiness
- **Goal**: Final hardening for production
- **What to demo**:
  - Cluster Autoscaler (scale EC2 nodes automatically)
  - HPA + Cluster Autoscaler working together
  - Multi-AZ deployment (high availability)
  - Pod Disruption Budgets
  - Cost optimization (right-sizing, spot instances)
  - Security review (network policies, pod security)
- **Concepts**: Cluster Autoscaler, multi-AZ, PDB, spot instances, security
- **Status**: NOT STARTED

---

## Folder Structure (Target)

```
natwest-microservices/
├── TRAINING-PLAN.md              ← This file
├── docs/                         ← Theory PDFs & references
│   ├── Microservices-1.pdf
│   ├── From Monolithic To Microservices-2.pdf
│   ├── MODULE-1-*.pdf
│   ├── MODULE-2-*.pdf
│   ├── MODULE-3-*.pdf
│   └── References.md
├── session-1/                    ← Spring Boot basics (done)
│   ├── design-patterns/
│   └── transfer-service/
├── ftgo-monolith/                ← Phase 0: Monolithic FTGO app
├── order-service/                ← Phase 8: Order microservice
├── kitchen-service/              ← Phase 6: Kitchen microservice
├── delivery-service/             ← Phase 5: Delivery microservice
├── restaurant-service/           ← Phase 3: Restaurant microservice
├── accounting-service/           ← Phase 7: Accounting microservice
├── notification-service/         ← Phase 2: Notification microservice
├── api-gateway/                  ← Phase 4: Spring Cloud Gateway
├── eureka-server/                ← Phase 4: Service Discovery
├── config-server/                ← Phase 9: Centralized Config
├── docker-compose.yml            ← Phase 11: Full local setup
├── k8s/                          ← Phase 14: Kubernetes manifests
├── helm/                         ← Phase 16: Helm charts
└── .github/workflows/            ← Phase 23: CI/CD pipelines
```

---

## Quick Reference — Patterns Covered

| Pattern | Phase | Reference |
|---------|-------|-----------|
| Monolithic Architecture | 0 | microservices.io/patterns/monolithic.html |
| Strangler Fig | 2 | microservices.io/patterns/refactoring/strangler-application.html |
| Async Messaging | 2 | — |
| Database per Service | 2, 3 | microservices.io/patterns/data/database-per-service.html |
| Decompose by Business Capability | 3 | microservices.io/patterns/decomposition/decompose-by-business-capability.html |
| API Gateway | 4 | microservices.io/patterns/apigateway.html |
| Service Discovery | 4 | microservices.io/patterns/service-registry.html |
| Anti-Corruption Layer | 5, 7 | microservices.io/patterns/refactoring/anti-corruption-layer.html |
| Saga | 6 | microservices.io/patterns/data/saga.html |
| Event-Driven Choreography | 6 | — |
| CQRS | 7 | microservices.io/patterns/data/cqrs.html |
| Circuit Breaker | 9 | microservices.io/patterns/reliability/circuit-breaker.html |
| Centralized Config | 9 | — |
| Distributed Tracing | 9 | microservices.io/patterns/observability/distributed-tracing.html |
| Centralized Logging | 9 | microservices.io/patterns/observability/application-logging.html |

---

## How to Resume

1. Open this file
2. Check **Current Status** at the top
3. Find the next phase with status `NOT STARTED`
4. Tell Claude: "Let's continue from Phase X"
5. Claude will read this plan and pick up exactly where we left off
