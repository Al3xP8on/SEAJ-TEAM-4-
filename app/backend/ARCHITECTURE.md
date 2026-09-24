# SEAJ Trading Platform - Simplified Architecture

## Overview

The SEAJ platform uses a **simplified unified architecture** with a single `pom.xml` and flat `src/` structure. All services (Account, Order, Positions) run in one Spring Boot application, organized logically by package.

### Architecture Benefits

✅ **Simpler build** - Single `mvn clean package` command  
✅ **Easier navigation** - All code in one place  
✅ **Fewer files** - One pom.xml instead of multiple  
✅ **Logical separation** - Code organized by package, not modules  
✅ **Single container** - Everything deployed together  
✅ **Future-proof** - Can extract services later if needed  

---

## Project Structure

```
app/backend/
├── pom.xml                    # Single Maven configuration
├── Dockerfile                 # Multi-stage Docker build
│
├── src/main/java/com/neueda/leap/
│   ├── gateway/               # API Gateway (routing)
│   │   └── controller/
│   │       └── GatewayController.java
│   ├── account/               # Account Service
│   │   ├── controller/
│   │   │   └── AccountController.java
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── order/                 # Order Service
│   │   ├── controller/
│   │   │   └── OrderController.java
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── position/              # Positions Service
│   │   ├── controller/
│   │   │   └── PositionController.java
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── shared/                # Shared utilities & exceptions
│   │   ├── exception/
│   │   │   ├── SeajException.java
│   │   │   └── ResourceNotFoundException.java
│   │   └── models/
│   └── SeajApplication.java   # Main entry point
│
├── src/main/resources/
│   └── application.properties # Spring Boot configuration
│
└── src/test/java/
    └── com/neueda/leap/       # Unit tests
```

## Services Overview

All services run within a single Spring Boot application, organized by package:

| Package | Service | Responsibility |
|---------|---------|-----------------|
| `com.neueda.leap.gateway` | API Gateway | Routing, health checks, logging |
| `com.neueda.leap.account` | Account Service | Accounts, balances, account status |
| `com.neueda.leap.order` | Order Service | Orders, execution, cancellation |
| `com.neueda.leap.position` | Positions Service | Holdings, P&L, reconciliation |
| `com.neueda.leap.shared` | Shared Code | Exceptions, utilities, constants |

---

## Project Structure

```
app/backend/
├── pom.xml                           # Parent POM (coordinates all modules)
├── Dockerfile                         # Single Dockerfile for unified app
│
├── shared/                            # Shared library (utilities, models, exceptions)
│   ├── pom.xml
│   └── src/main/java/com/neueda/leap/shared/
│       ├── exception/
│       │   ├── SeajException.java
│       │   └── ResourceNotFoundException.java
│       ├── models/
│       ├── utils/
│       └── constants/
│
├── api-gateway/                       # API Gateway module (routing, auth)
│   ├── pom.xml
│   └── src/main/java/com/neueda/leap/gateway/
│       ├── controller/
│       ├── filter/
│       └── config/
│
├── account-service/                   # Account Service module
│   ├── pom.xml
│   └── src/main/java/com/neueda/leap/account/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
├── order-service/                     # Order Service module
│   ├── pom.xml
│   └── src/main/java/com/neueda/leap/order/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
├── positions-service/                 # Positions Service module
│   ├── pom.xml
│   └── src/main/java/com/neueda/leap/position/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
└── unified-app/                       # Main application (bundles all services)
    ├── pom.xml
    ├── src/main/java/com/neueda/leap/
    │   └── SeajApplication.java
    └── src/main/resources/
        └── application.properties
```

---

## Module Descriptions

### 1. **shared** - Common Library
Contains shared code used by all services:
- Custom exceptions
- Data Transfer Objects (DTOs)
- Utility functions
- Constants and enums
- Validation utilities

**Dependencies:** None (used by all)  
**Depends On:** Spring Boot

### 2. **api-gateway** - Request Routing
Central entry point for all API requests:
- Request routing logic
- Authentication/Authorization filters
- Rate limiting
- Request/response logging
- CORS configuration

**Dependencies:** shared  
**Depends On:** Spring Cloud Gateway

### 3. **account-service** - Account Management
Handles trading account operations:
- Account CRUD operations
- Balance management
- Account status management
- Account history

**Dependencies:** shared  
**Depends On:** Spring Data JPA, PostgreSQL

### 4. **order-service** - Order Management
Handles order lifecycle:
- Order creation and validation
- Order execution
- Order cancellation
- Order status tracking
- Order history

**Dependencies:** shared  
**Depends On:** Spring Data JPA, PostgreSQL

### 5. **positions-service** - Position Management
Handles trading positions:
- Position tracking
- P&L calculations
- Position reconciliation
- Position history

**Dependencies:** shared  
**Depends On:** Spring Data JPA, PostgreSQL

### 6. **unified-app** - Main Application
Spring Boot entry point that bundles all services:
- Imports all service modules
- Configures component scanning
- Manages application lifecycle
- Provides single JAR for deployment

**Dependencies:** All service modules  
**Depends On:** All above modules

---

## Database Architecture

All services share a **single PostgreSQL database** with separate schemas/tables:

```sql
-- Account Service Tables
accounts
balances
account_status

-- Order Service Tables
orders
order_history
order_status

-- Positions Service Tables
positions
position_history
current_prices

-- Shared Tables
clients
instruments
market_data
```

---

## Build & Deployment

### Building the Application

```bash
# Build all modules and create JAR
cd app/backend
mvn clean package -DskipTests

# Output: unified-app/target/seaj-unified-app-0.1.0.jar
```

### Docker Build

```bash
# Build Docker image
docker-compose build

# Start the application
docker-compose up -d

# View logs
docker-compose logs -f app
```

### Endpoints

| Service | Endpoint | Purpose |
|---------|----------|---------|
| Gateway | `GET /api/gateway/health` | Gateway health check |
| Gateway | `GET /api/gateway/info` | Platform info |
| Accounts | `GET /api/accounts` | List all accounts |
| Accounts | `GET /api/accounts/{id}` | Get account by ID |
| Orders | `GET /api/orders` | List all orders |
| Orders | `POST /api/orders` | Create new order |
| Orders | `GET /api/orders/{id}` | Get order by ID |
| Positions | `GET /api/positions` | List all positions |
| Positions | `GET /api/positions/accounts/{id}` | Get account positions |

---

## Development Workflow

### Adding New Functionality

1. **Choose appropriate module** based on responsibility
2. **Create controller/service/repository** in that module
3. **Add dependencies** to module's pom.xml if needed
4. **Use shared** module for common code
5. **Test locally** with `mvn test` in module directory
6. **Build unified app** with `mvn package` in backend directory

### Example: Adding Account Feature

```bash
# Create new controller in account-service module
# File: app/backend/account-service/src/main/java/com/neueda/leap/account/controller/KYCController.java

@RestController
@RequestMapping("/api/accounts/kyc")
public class KYCController {
    // Implementation
}

# Rebuild application
cd app/backend
mvn clean package -DskipTests
```

---

## Migration Path to True Microservices

When you're ready to extract individual services into true microservices:

1. **Extract module** into separate repository
2. **Remove from pom.xml** modules list
3. **Add REST client** in unified-app to call external service
4. **Update docker-compose** to add new service container
5. **Configure service discovery** (Consul, Eureka, etc.)

Each module is already structured to make this transition smooth!

---

## Environment Variables

Create `.env` file in project root:

```bash
# Database Configuration
POSTGRES_DB=SEAJ_db_DEMO
POSTGRES_PASSWORD=postgres_password
DB_PORT=5432
DB_USERNAME=postgres

# Application Configuration
APP_PORT=8080
ENVIRONMENT=development
SPRING_DATASOURCE_USERNAME=postgres
```

---

## Key Technologies

- **Java 21** with Spring Boot 3.3.0
- **Maven** for build management
- **PostgreSQL** for data persistence
- **Docker** & **Docker Compose** for containerization
- **Spring Data JPA** for database operations

---

## Next Steps

1. Migrate existing backend-java code into appropriate modules
2. Implement inter-service communication (REST calls between services)
3. Add event-driven communication using message queue (RabbitMQ/Kafka)
4. Implement comprehensive logging and monitoring
5. Add API documentation (Swagger/OpenAPI)
6. Setup CI/CD pipeline with Jenkins

---

## Documentation References

- [API Documentation](./docs/)
- [Database Schema](./infra/db/)
- [Configuration Guide](./app/backend/unified-app/src/main/resources/)

