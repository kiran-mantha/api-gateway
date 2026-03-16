# API Gateway — Spring Boot + Docker

A production-ready API Gateway built with Spring Cloud Gateway that handles authentication, rate limiting, and traffic routing for microservices. Everything runs in Docker with zero local setup required beyond Docker itself.

---

## Table of Contents

- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [API Reference](#api-reference)
  - [Authentication](#authentication)
  - [User Service](#user-service)
  - [Order Service](#order-service)
  - [Actuator](#actuator)
- [Rate Limiting](#rate-limiting)
- [How Authentication Works](#how-authentication-works)
- [Filter Chain](#filter-chain)
- [Troubleshooting](#troubleshooting)

---

## Architecture

```
Client
  │
  ▼
API Gateway (port 8080)
  │   ├── AuthFilter       → validates JWT
  │   ├── RateLimitFilter  → token bucket via Redis
  │   └── LoggingFilter    → correlation ID tracing
  │
  ├──► User Service  (port 8081)  — auth + user data
  └──► Order Service (port 8082)  — order data
  
Infrastructure
  ├── Redis      (port 6379)  — rate limit buckets
  └── PostgreSQL (port 5432)  — users, API keys
```

Every request enters through the gateway on port `8080`. The gateway validates the JWT, checks the rate limit bucket in Redis, then forwards the request to the appropriate microservice. Microservices never talk to clients directly.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Gateway | Spring Cloud Gateway 2023.0.2 (reactive, Netty) |
| Auth tokens | JWT (jjwt 0.12.5) |
| Rate limiting | Redis 7.2 — token bucket algorithm, atomic Lua script |
| User storage | PostgreSQL 16 — bcrypt hashed passwords |
| Reactive DB | R2DBC (gateway) + JPA (user-service) |
| Resilience | Resilience4j circuit breaker |
| Runtime | Java 21, Spring Boot 3.3.0 |
| Containers | Docker + Docker Compose |

---

## Project Structure

```
api-gateway-project/
├── docker-compose.yml
├── README.md
│
├── gateway/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── config/
│       │   │   ├── RedisConfig.java
│       │   │   ├── RouteConfig.java
│       │   │   └── RateLimitProperties.java
│       │   ├── filter/
│       │   │   ├── AuthFilter.java
│       │   │   ├── RateLimitFilter.java
│       │   │   └── LoggingFilter.java
│       │   └── util/
│       │       └── JwtUtil.java
│       └── resources/
│           ├── application.yml
│           └── scripts/
│               └── rate_limit.lua
│
├── user-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/userservice/
│       │   ├── UserServiceApplication.java
│       │   ├── config/
│       │   │   └── SecurityConfig.java
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   └── UserController.java
│       │   ├── dto/
│       │   │   ├── LoginRequest.java
│       │   │   ├── LoginResponse.java
│       │   │   └── RegisterRequest.java
│       │   ├── entity/
│       │   │   └── User.java
│       │   ├── repository/
│       │   │   └── UserRepository.java
│       │   ├── service/
│       │   │   └── AuthService.java
│       │   └── util/
│       │       └── JwtUtil.java
│       └── resources/
│           └── application.yml
│
├── order-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/orderservice/
│       │   ├── OrderServiceApplication.java
│       │   └── controller/
│       │       └── OrderController.java
│       └── resources/
│           └── application.yml
│
└── postgres/
    └── init.sql
```

---

## Prerequisites

- [Docker Desktop](https://www.docker.com/products/docker-desktop/) — version 24+ recommended
- [Docker Compose](https://docs.docker.com/compose/) — included with Docker Desktop
- `curl` — for running the API examples below
- `python` — only for pretty-printing JSON (`python -m json.tool`)

That is everything. No Java, no Maven, no local database needed.

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/your-org/api-gateway-project.git
cd api-gateway-project
```

### 2. Start everything

```bash
docker compose up --build
```

This will:
- Build all three service images from source
- Start PostgreSQL and run `init.sql` (creates tables, seeds test users)
- Start Redis
- Wait for health checks to pass before starting the gateway

First build takes 3–5 minutes while Maven downloads dependencies. Subsequent builds are much faster due to Docker layer caching.

### 3. Verify everything is running

```bash
docker compose ps
```

All services should show `healthy`:

```
NAME             STATUS
api-gateway      running (healthy)
user-service     running (healthy)
order-service    running (healthy)
redis            running (healthy)
postgres         running (healthy)
```

### 4. Confirm the gateway is up

```bash
curl http://localhost:8080/actuator/health | python -m json.tool
```

Expected:
```json
{
    "status": "UP"
}
```

### 5. Stop everything

```bash
docker compose down
```

To also wipe the database volume (full reset):

```bash
docker compose down -v
```

---

## Environment Variables

All variables have safe defaults for local development. Override them in `docker-compose.yml` or pass them via a `.env` file for different environments.

| Variable | Service | Default | Description |
|---|---|---|---|
| `JWT_SECRET` | gateway, user-service | `my-super-secret-key-...` | Shared secret for signing/verifying JWTs. Must be 32+ characters. |
| `JWT_EXPIRY_MS` | user-service | `3600000` | Token lifetime in milliseconds (default: 1 hour). |
| `SPRING_DATA_REDIS_HOST` | gateway | `redis` | Redis hostname (Docker service name). |
| `SPRING_DATA_REDIS_PORT` | gateway | `6379` | Redis port. |
| `DB_HOST` | gateway, user-service | `postgres` | PostgreSQL hostname. |
| `DB_USERNAME` | user-service | `gateway_user` | PostgreSQL username. |
| `DB_PASSWORD` | user-service | `gateway_pass` | PostgreSQL password. |
| `SERVER_PORT` | all services | `8080/8081/8082` | HTTP port each service listens on. |

> **Important:** Never commit real secrets to source control. For production, use a secrets manager such as AWS Secrets Manager, HashiCorp Vault, or Docker Secrets.

---

## API Reference

### Seeded Test Users

Two users are inserted automatically when the database is first created:

| Email | Password | Role |
|---|---|---|
| `john@example.com` | `password123` | `USER` |
| `admin@example.com` | `password123` | `ADMIN` |

---

### Authentication

All auth endpoints are public — no token required.

#### POST `/api/auth/login`

Validates credentials against the database and returns a signed JWT.

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"password123"}' \
  | python -m json.tool
```

Response `200 OK`:
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "type": "Bearer",
    "userId": 1,
    "email": "john@example.com",
    "role": "USER"
}
```

Response `400 Bad Request` (wrong credentials):
```json
{
    "message": "Invalid email or password"
}
```

---

#### POST `/api/auth/register`

Creates a new user account and returns a token immediately.

```bash
curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"newuser@example.com","password":"securepass123"}' \
  | python -m json.tool
```

Response `201 Created`:
```json
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "type": "Bearer",
    "userId": 3,
    "email": "newuser@example.com",
    "role": "USER"
}
```

---

### Using a Token

Save your token to a shell variable to reuse it across requests:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"john@example.com","password":"password123"}' \
  | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

echo $TOKEN
```

All protected endpoints require the `Authorization: Bearer <token>` header. Without it:

```bash
curl -s http://localhost:8080/api/users/1 | python -m json.tool
```

Response `401 Unauthorized`:
```json
{
    "status": 401,
    "error": "Unauthorized",
    "message": "Missing Authorization header"
}
```

---

### User Service

Base path: `/api/users` — routed to `user-service:8081`

#### GET `/api/users/{id}`

Fetch a user by ID.

```bash
curl -s http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $TOKEN" \
  | python -m json.tool
```

Response `200 OK`:
```json
{
    "id": 1,
    "name": "John Doe",
    "email": "john@example.com",
    "source": "user-service"
}
```

---

#### GET `/api/users`

List all users.

```bash
curl -s http://localhost:8080/api/users \
  -H "Authorization: Bearer $TOKEN" \
  | python -m json.tool
```

---

#### GET `/api/auth/headers`

Debug endpoint — echoes back the headers the gateway injected. Useful for verifying the filter chain is working.

```bash
curl -s http://localhost:8080/api/auth/headers \
  -H "Authorization: Bearer $TOKEN" \
  | python -m json.tool
```

Response `200 OK`:
```json
{
    "X-User-Id":    "1",
    "X-User-Role":  "USER",
    "X-User-Email": "john@example.com"
}
```

These headers are set by the gateway after validating the JWT. Downstream services should read user identity from these headers, never from the token directly.

---

### Order Service

Base path: `/api/orders` — routed to `order-service:8082`

#### GET `/api/orders/{id}`

Fetch an order by ID.

```bash
curl -s http://localhost:8080/api/orders/1 \
  -H "Authorization: Bearer $TOKEN" \
  | python -m json.tool
```

Response `200 OK`:
```json
{
    "id": 1,
    "source": "order-service"
}
```

---

### Actuator

Gateway management endpoints — useful for debugging and monitoring.

#### GET `/actuator/health`

```bash
curl -s http://localhost:8080/actuator/health | python -m json.tool
```

#### GET `/actuator/gateway/routes`

Lists all registered routes with their predicates and filters.

```bash
curl -s http://localhost:8080/actuator/gateway/routes | python -m json.tool
```

#### GET `/actuator/gateway/globalfilters`

Lists all active global filters and their execution order.

```bash
curl -s http://localhost:8080/actuator/gateway/globalfilters | python -m json.tool
```

#### POST `/actuator/gateway/refresh`

Reloads route definitions without restarting the gateway.

```bash
curl -s -X POST http://localhost:8080/actuator/gateway/refresh
```

---

## Rate Limiting

The gateway enforces per-user rate limits using a **token bucket** algorithm backed by Redis. Limits are applied per `userId` extracted from the JWT.

| Role | Requests allowed | Window |
|---|---|---|
| `USER` | 60 | per minute |
| `ADMIN` | 1000 | per minute |

Every response includes rate limit headers so clients can self-throttle:

```
X-RateLimit-Limit:     60
X-RateLimit-Remaining: 45
X-RateLimit-Window:    60s
```

Check them with:

```bash
curl -v http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer $TOKEN" 2>&1 | grep "< X-RateLimit"
```

When the bucket is empty, the gateway returns `429 Too Many Requests`:

```json
{
    "status": 429,
    "error": "Too Many Requests",
    "message": "Rate limit exceeded. Retry after 60 seconds."
}
```

The response also includes a `Retry-After` header with the number of seconds to wait.

To test rate limiting, fire 70 rapid requests and watch `429` kick in after 60:

```bash
for i in $(seq 1 70); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    http://localhost:8080/api/users/1 \
    -H "Authorization: Bearer $TOKEN")
  echo "Request $i: HTTP $STATUS"
done
```

To inspect a user's bucket state directly in Redis:

```bash
docker exec -it redis redis-cli HGETALL "rate_limit:user:1"
```

---

## How Authentication Works

```
POST /api/auth/login
  │
  ▼
Gateway routes to user-service (public path, no auth check)
  │
  ▼
user-service queries PostgreSQL for the user by email
  │
  ▼
BCrypt verifies the submitted password against the stored hash
  │
  ▼
user-service issues a signed JWT containing userId, email, role
  │
  ▼
Client stores the token and sends it on every subsequent request
  │
  ▼
Gateway AuthFilter validates the JWT signature using the shared secret
(no database call — signature verification is local and instant)
  │
  ▼
Claims (userId, role, email) are extracted and forwarded as
trusted request headers to downstream services:
  X-User-Id:    1
  X-User-Role:  USER
  X-User-Email: john@example.com
```

The JWT secret is shared between the gateway and user-service via the `JWT_SECRET` environment variable. The gateway never needs to call the user-service to validate a token — it verifies the signature locally. This keeps authentication fast and keeps the user-service from becoming a bottleneck.

---

## Filter Chain

Global filters execute in this order on every request:

| Order | Filter | Responsibility |
|---|---|---|
| -100 | `AuthFilter` | Validate JWT, reject unauthenticated requests, inject user headers |
| -90 | `RateLimitFilter` | Check Redis token bucket, reject if limit exceeded |
| -80 | `LoggingFilter` | Assign correlation ID, log request/response metadata |
| 0+ | Spring Cloud Gateway | Route to downstream service |

Public paths that bypass the filter chain entirely:
- `/api/auth/login`
- `/api/auth/register`
- `/actuator/**`

---

## Troubleshooting

**Gateway fails to start**

Check the logs:
```bash
docker compose logs api-gateway
```

The most common cause is the gateway starting before Redis or PostgreSQL is ready. The `depends_on` health checks handle this automatically, but if you see connection errors on first boot, restart just the gateway:
```bash
docker compose restart api-gateway
```

---

**401 on every request even with a valid token**

Verify the `JWT_SECRET` is identical in both the gateway and user-service environment variables in `docker-compose.yml`. A mismatch means the gateway cannot verify tokens issued by the user-service.

---

**429 Too Many Requests immediately**

Your bucket may still be empty from a previous test run. Either wait for the window to expire (60 seconds) or flush the bucket manually:
```bash
docker exec -it redis redis-cli DEL "rate_limit:user:1"
```

---

**Postgres init.sql did not run**

The init script only runs when the data volume is created for the first time. If the volume already exists from a previous run, the script is skipped. Do a full reset:
```bash
docker compose down -v
docker compose up --build
```

---

**Rebuilding after code changes**

To rebuild only the service you changed:
```bash
docker compose up --build api-gateway
docker compose up --build user-service
```

To rebuild everything from scratch with no cache:
```bash
docker compose build --no-cache
docker compose up
```
