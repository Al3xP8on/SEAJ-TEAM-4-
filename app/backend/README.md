# SEAJ Backend - Modular Monolith Architecture

## Quick Start

### Prerequisites
- Java 21 JDK
- Maven 3.9.5+
- Docker & Docker Compose
- PostgreSQL 15+ (or use Docker)

### Setup & Run

```bash
# 1. Clone the repository and navigate to backend
cd app/backend

# 2. Create environment file
cp ../.env.example .env
# Edit .env with your database credentials

# 3. Build the application
mvn clean package -pl unified-app -am

# 4. Start with Docker Compose
docker-compose up -d

# 5. Test the application
curl http://localhost:8080/api/gateway/health
```

## Project Structure

This project uses a **Modular Monolith** architecture:

```
backend/
├── pom.xml                    # Parent POM coordinates all modules
├── Dockerfile                 # Multi-stage Docker build
├── ARCHITECTURE.md            # Detailed architecture documentation
├── MIGRATION_GUIDE.md         # Guide for migrating existing code
│
├── shared/                    # Shared library (used by all)
│   └── src/main/java/com/neueda/leap/shared/
│       ├── exception/         # Custom exceptions
│       ├── models/            # DTOs and data models
│       └── utils/             # Utility functions
│
├── api-gateway/               # API Gateway module
│   └── src/main/java/com/neueda/leap/gateway/
│       ├── controller/        # Gateway controllers
│       ├── filter/            # Request filters
│       └── config/            # Gateway configuration
│
├── account-service/           # Account Service module
│   └── src/main/java/com/neueda/leap/account/
│       ├── controller/        # REST endpoints
│       ├── service/           # Business logic
│       ├── repository/        # Database access
│       ├── entity/            # JPA entities
│       └── dto/               # Data transfer objects
│
├── order-service/             # Order Service module
│   └── src/main/java/com/neueda/leap/order/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
├── positions-service/         # Positions Service module
│   └── src/main/java/com/neueda/leap/position/
│       ├── controller/
│       ├── service/
│       ├── repository/
│       ├── entity/
│       └── dto/
│
└── unified-app/               # Main Spring Boot application
    ├── pom.xml
    ├── src/main/java/com/neueda/leap/
    │   └── SeajApplication.java  # Entry point
    └── src/main/resources/
        └── application.properties  # Configuration
```

## Modules Overview

| Module | Purpose | Responsibilities |
|--------|---------|------------------|
| **shared** | Common code | Exceptions, DTOs, utilities, constants |
| **api-gateway** | Request routing | Authentication, routing, rate limiting |
| **account-service** | Account management | Accounts, balances, account status |
| **order-service** | Order management | Orders, execution, cancellation |
| **positions-service** | Position tracking | Positions, P&L, reconciliation |
| **unified-app** | Main application | Entry point, component scanning, lifecycle |

## Building

### Build All Modules
```bash
mvn clean package
```

### Build Specific Module
```bash
# Build account service and dependencies
mvn clean package -pl account-service -am

# Build unified app with all dependencies
mvn clean package -pl unified-app -am
```

### Skip Tests
```bash
mvn clean package -DskipTests
```

### Run Tests
```bash
# All tests
mvn test

# Specific module
mvn test -pl account-service

# Skip integration tests (run unit only)
mvn test -DskipITs
```

## Running

### Using Docker Compose (Recommended)
```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop all services
docker-compose down

# Clean up volumes
docker-compose down -v
```

### Running Locally (Development)
```bash
# Build first
mvn clean package -pl unified-app -am

# Start database only
docker-compose up -d db

# Run application (IDE or command line)
mvn spring-boot:run -pl unified-app
```

## API Endpoints

### Gateway (Routing)
```
GET  /api/gateway/health          - Health check
GET  /api/gateway/info            - Platform info
```

### Account Service
```
GET    /api/accounts              - List all accounts
GET    /api/accounts/{id}         - Get account details
POST   /api/accounts              - Create account
PUT    /api/accounts/{id}         - Update account
DELETE /api/accounts/{id}         - Delete account
```

### Order Service
```
GET    /api/orders                - List all orders
GET    /api/orders/{id}           - Get order details
POST   /api/orders                - Create order
PUT    /api/orders/{id}           - Update order
DELETE /api/orders/{id}           - Cancel order
```

### Positions Service
```
GET    /api/positions             - List all positions
GET    /api/positions/{id}        - Get position details
GET    /api/positions/accounts/{accountId} - Get account positions
```

## Configuration

Main configuration file: `unified-app/src/main/resources/application.properties`

### Key Properties
```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:postgresql://db:5432/SEAJ_db_DEMO
spring.datasource.username=postgres
spring.datasource.password=postgres

# JPA
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false

# Logging
logging.level.root=INFO
logging.level.com.neueda.leap=DEBUG
logging.file.name=logs/app.log
```

Override with environment variables:
```bash
export DB_HOST=custom-db-host
export APP_PORT=9000
docker-compose up
```

## Development Workflow

### Adding New Feature to Account Service

1. **Create entity** (if database interaction)
```bash
# File: account-service/src/main/java/com/neueda/leap/account/entity/AccountKYC.java
@Entity
@Table(name = "account_kyc")
public class AccountKYC {
    // fields and methods
}
```

2. **Create repository** (if database interaction)
```bash
# File: account-service/src/main/java/com/neueda/leap/account/repository/AccountKYCRepository.java
@Repository
public interface AccountKYCRepository extends JpaRepository<AccountKYC, Long> {
    // custom queries
}
```

3. **Create service** (business logic)
```bash
# File: account-service/src/main/java/com/neueda/leap/account/service/AccountKYCService.java
@Service
public class AccountKYCService {
    @Autowired
    private AccountKYCRepository repository;
    // business logic
}
```

4. **Create controller** (REST endpoint)
```bash
# File: account-service/src/main/java/com/neueda/leap/account/controller/AccountKYCController.java
@RestController
@RequestMapping("/api/accounts/kyc")
public class AccountKYCController {
    @Autowired
    private AccountKYCService service;
    
    @PostMapping
    public ResponseEntity<?> createKYC(@RequestBody KYCRequest request) {
        // implementation
    }
}
```

5. **Write tests**
```bash
# File: account-service/src/test/java/com/neueda/leap/account/service/AccountKYCServiceTest.java
```

6. **Build and test**
```bash
mvn clean test -pl account-service
mvn clean package -pl unified-app -am
```

### Migrating Existing Code

See [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md) for detailed instructions.

## Troubleshooting

### Application won't start
```bash
# Check logs
docker-compose logs app

# Verify database is running
docker-compose logs db

# Rebuild everything
docker-compose down -v
docker-compose build --no-cache
docker-compose up -d
```

### Port already in use
```bash
# Change port in .env
APP_PORT=9000

# Or kill process using port
lsof -i :8080
kill -9 <PID>
```

### Compilation errors
```bash
# Clean build
mvn clean

# Rebuild with verbose output
mvn -X clean package

# Check for missing dependencies
mvn dependency:tree
```

### Database connection issues
```bash
# Test connection
docker-compose exec db psql -U postgres -d SEAJ_db_DEMO -c "SELECT 1"

# Reset database
docker-compose down -v
docker-compose up -d db
```

## Performance Tips

1. **Use profiles** for different environments
2. **Enable query caching** for frequently accessed data
3. **Use indices** on frequently queried columns
4. **Implement pagination** for large result sets
5. **Use connection pooling** (HikariCP)

## Security Considerations

- ✅ Validate all inputs
- ✅ Use parameterized queries (JPA prevents SQL injection)
- ✅ Implement authentication on sensitive endpoints
- ✅ Use HTTPS in production
- ✅ Secure database credentials in environment variables
- ✅ Implement rate limiting
- ✅ Log security events

## CI/CD Integration

The `Jenkinsfile` in the project root is configured to:
1. Build all modules
2. Run tests
3. Build Docker image
4. Run smoke tests
5. Push to registry (configured in Jenkins)

See [Jenkinsfile](../../../Jenkinsfile) for details.

## Documentation

- [Architecture Overview](./ARCHITECTURE.md) - Detailed architecture and design
- [Migration Guide](./MIGRATION_GUIDE.md) - Migrating from monolith
- [API Documentation](../../../docs/) - API specifications

## Support

For questions or issues:
1. Check logs: `docker-compose logs`
2. Review [ARCHITECTURE.md](./ARCHITECTURE.md)
3. Check [MIGRATION_GUIDE.md](./MIGRATION_GUIDE.md)
4. Contact the SEAJ team

---

**Last Updated:** 2026-09-23  
**Version:** 0.1.0  
**Status:** Modular monolith structure implemented
