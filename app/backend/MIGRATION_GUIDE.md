# Migration Guide: Monolithic Java to Modular Monolith

## Overview

This guide helps migrate code from the old monolithic `backend-java` structure to the new modular architecture.

## Key Changes

### Old Structure
```
app/backend/backend-java/
├── src/main/java/com/neueda/leap/
│   ├── accounts/
│   ├── orders/
│   ├── positions/
│   └── other/
└── src/main/resources/
    └── application.properties
```

### New Structure
```
app/backend/
├── shared/                    # NEW: Shared code
├── api-gateway/              # NEW: Gateway code
├── account-service/          # Account-specific code
├── order-service/            # Order-specific code
├── positions-service/        # Position-specific code
└── unified-app/              # NEW: Main app entry point
```

## Migration Steps

### Step 1: Identify Service Ownership

Review your code and categorize into services:

| Original Package | New Module | New Package |
|------------------|-----------|-----------|
| `com.neueda.leap.accounts.*` | `account-service` | `com.neueda.leap.account.*` |
| `com.neueda.leap.orders.*` | `order-service` | `com.neueda.leap.order.*` |
| `com.neueda.leap.positions.*` | `positions-service` | `com.neueda.leap.position.*` |
| `com.neueda.leap.shared.*` | `shared` | `com.neueda.leap.shared.*` |
| `com.neueda.leap.gateway.*` | `api-gateway` | `com.neueda.leap.gateway.*` |

### Step 2: Move Code to Appropriate Modules

**Example: Moving Account Controller**

```bash
# Old location
app/backend/backend-java/src/main/java/com/neueda/leap/accounts/AccountController.java

# New location
app/backend/account-service/src/main/java/com/neueda/leap/account/AccountController.java

# Don't forget to update package declaration!
# OLD: package com.neueda.leap.accounts;
# NEW: package com.neueda.leap.account;
```

### Step 3: Move JPA Entities

**Move to appropriate service module:**

```bash
# Account entities
app/backend/account-service/src/main/java/com/neueda/leap/account/entity/

# Order entities
app/backend/order-service/src/main/java/com/neueda/leap/order/entity/

# Position entities
app/backend/positions-service/src/main/java/com/neueda/leap/position/entity/
```

### Step 4: Move DTOs and Models to Shared

```bash
# Common DTOs and models
app/backend/shared/src/main/java/com/neueda/leap/shared/models/
```

### Step 5: Update Dependencies

For each file moved, update imports:

```java
// OLD
import com.neueda.leap.accounts.AccountService;
import com.neueda.leap.shared.exceptions.ServiceException;

// NEW
import com.neueda.leap.account.service.AccountService;
import com.neueda.leap.shared.exception.ServiceException;
```

### Step 6: Move Configuration Files

```bash
# Application properties
app/backend/unified-app/src/main/resources/application.properties

# Service-specific configs (if needed)
app/backend/account-service/src/main/resources/account-service.yml
app/backend/order-service/src/main/resources/order-service.yml
```

### Step 7: Move Test Files

```bash
# Account service tests
app/backend/account-service/src/test/java/com/neueda/leap/account/

# Order service tests
app/backend/order-service/src/test/java/com/neueda/leap/order/

# Shared tests
app/backend/shared/src/test/java/com/neueda/leap/shared/
```

## Validation Checklist

After migration, verify:

- [ ] All Java files moved to correct modules
- [ ] Package names updated to match new structure
- [ ] All imports corrected
- [ ] No circular dependencies between modules
- [ ] All tests pass: `mvn clean test`
- [ ] Application builds: `mvn clean package -DskipTests -pl unified-app -am`
- [ ] Docker image builds: `docker-compose build`
- [ ] Application starts: `docker-compose up`
- [ ] API endpoints respond: `curl http://localhost:8080/api/gateway/health`

## Troubleshooting

### Error: "Cannot find symbol" after moving files

**Solution:** Ensure package declaration matches new location:
```java
// Correct
package com.neueda.leap.account.controller;

// Wrong (old)
package com.neueda.leap.accounts.controller;
```

### Error: "Compilation failure" in Maven build

**Solution:** Check module pom.xml has all required dependencies:
```xml
<!-- In account-service/pom.xml -->
<dependency>
    <groupId>com.neueda.leap</groupId>
    <artifactId>seaj-shared</artifactId>
</dependency>
```

### Error: "Class not found" when starting application

**Solution:** Verify `@ComponentScan` in `SeajApplication.java` includes package:
```java
@ComponentScan(basePackages = {
    "com.neueda.leap.shared",
    "com.neueda.leap.gateway",
    "com.neueda.leap.account",
    "com.neueda.leap.order",
    "com.neueda.leap.position"
})
```

## Build Commands

```bash
# Build all modules
cd app/backend
mvn clean package

# Build only unified-app (faster, includes dependencies)
mvn clean package -pl unified-app -am

# Run tests only for specific module
mvn test -pl account-service

# Skip tests and build
mvn clean package -DskipTests

# Build Docker image
docker-compose build

# Run application
docker-compose up -d

# View logs
docker-compose logs -f app
```

## Timeline Estimate

- **Small codebase** (< 20 classes): 1-2 hours
- **Medium codebase** (20-50 classes): 2-4 hours  
- **Large codebase** (> 50 classes): 4-8 hours

**Tips to speed up migration:**
- Use IDE refactoring tools (Rename Package, Move Files)
- Migrate one service at a time
- Keep git commits small for easier review
- Run tests frequently to catch issues early

---

Need help? Check [ARCHITECTURE.md](./ARCHITECTURE.md) for structure details.
