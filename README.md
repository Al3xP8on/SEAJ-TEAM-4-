# Enterprise Trading Platform: SEAJ

## SEAJ Group 4: 
- Samson
- Ella
- Alex
- Jahangir

# Branching Strategy
## Gitflow Branching Strategy
    - Main
    - Develop
    - Feature branches merged into develop
    - Feature branch naming conventions: feat/indicative-branch-name

Cleaner, More Structured. Each one of us can work on different branches and features and merge in an orginised manner.

## Project Managers
 - Sprint 2: Ella
 - Sprint 3: Jahangir
 - Sprint 4: Samson
 - Sprint 5: Alex
 - Sprint 6: Jahangir
 - Sprint 7: Ella
 - Sprint 8: Samson

# Application Setup & Installation
## Prerequisites

Ensure you have the following installed on your system:

- **Docker** (version 20.10+) and **Docker Compose** (version 2.0+)
- **Java 21** (for local Maven builds)
  - Verify: `java -version`
- **Maven 3.8+** (for building the Java backend)
  - Verify: `mvn --version`
- **Git** (for cloning the repository)
- **curl** or **Postman** (for testing API endpoints)

## Project Architecture

The SEAJ platform consists of:

- **Spring Boot API** (Java) - Order management & trading
- **PostgreSQL** - Relational database for accounts, positions, orders
- **Apache Kafka** - Event streaming (orders, executions, market-data)
- **Execution Engine** - Processes trade orders from Kafka
- **Auth Stub** - JWT token generation for API authentication
- **SonarQube** - Code quality and security analysis
- **Kafka UI** - Real-time monitoring of Kafka topics

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/Al3xP8on/SEAJ-TEAM-4-.git
cd newSEAJ
```

### 2. Configure Environment Variables

Create or update the `.env` file in the project root:

```bash
# Database Configuration
POSTGRES_DB=seaj_db
POSTGRES_PASSWORD=changeme

# Spring Boot Configuration
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/seaj_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=changeme

# JWT Secret (used for API authentication)
JWT_SECRET=your-secret-key-here

# Kafka Configuration (Optional - defaults provided)
ENGINE_MIN_DELAY=500ms
ENGINE_MAX_DELAY=2000ms
ENGINE_MAX_PRICE_IMPROVEMENT_BPS=50

# SonarQube (Optional - for code analysis)
SONAR_TOKEN=your-token-here
```

### 3. Start All Services

```bash
# Navigate to project root
cd /home/ec2-user/newSEAJ

# Build and start all containers
docker-compose up -d --build

# Verify all services are running and healthy
docker-compose ps
```

**Expected Output:**
```
NAME                    IMAGE                           STATUS
seaj_app                newseaj-app                     Up 3 minutes
seaj_postgres_db        newseaj-db                      Up 3 minutes
seaj_kafka              confluentinc/cp-kafka:7.6.1     Up 3 minutes (healthy)
seaj_auth_stub          newseaj-auth-stub               Up 3 minutes
seaj_kafka_ui           provectuslabs/kafka-ui:latest   Up 3 minutes
seaj_execution_engine   newseaj-execution-engine        Up 3 minutes (healthy)
seaj_sonarqube          sonarqube:9.9-community         Up 3 minutes
```

## Accessing the Services

Once all containers are running, access them at:

| Service | URL | Credentials |
|---------|-----|-------------|
| **Swagger API** | http://localhost:8081/api/swagger-ui/index.html | N/A |
| **Kafka UI** | http://localhost:8090 | N/A |
| **SonarQube** | http://localhost:8088 | admin/admin |
| **PostgreSQL** | localhost:5433 | `POSTGRES_USER` / `POSTGRES_PASSWORD` from `.env` |

## Testing the Application

### 1. Generate Authentication Token

```bash
TOKEN=$(curl -s -X POST http://localhost:3000/token \
  -H 'Content-Type: application/json' \
  -d '{}' | jq -r '.token')

echo "Token: $TOKEN"
```

### 2. Create a Test Order

```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC-0001",
    "symbol": "AAPL",
    "quantity": 50,
    "orderSide": "BUY",
    "orderType": "MARKET",
    "timeInForce": "GTC",
    "price": 150.50
  }'
```

### 3. Check Order Status

```bash
curl -X GET http://localhost:8081/api/v1/orders/account/ACC-0001 \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 4. View Positions

```bash
curl -X GET http://localhost:8081/api/v1/positions/1/positions \
  -H "Authorization: Bearer $TOKEN" | jq .
```

### 5. Monitor Kafka Messages

Visit **Kafka UI** at http://localhost:8090:
- Click on the "local" cluster
- Browse topics: `trades`, `trade-events`, `market-data`
- View messages flowing through the system in real-time

## Kafka Message Flow

When you create an order via the API, here's what happens:

```
1. Order Created (POST /v1/orders)
   ↓
2. Message published to "trades" topic
   ↓
3. Kafka partitions message by accountId
   ↓
4. Execution Engine consumes message
   ↓
5. Order status updates: NEW → PENDING → FILLED
   ↓
6. Execution event published to "trade-events" topic
   ↓
7. Position quantities updated in database
```

## Building & Testing Locally

### Build Java Backend

```bash
cd app/backend/backend-java

# Compile and run tests
mvn clean package

# Or skip tests for faster builds
mvn clean package -DskipTests
```

### Run SonarQube Code Analysis

```bash
cd app/backend/backend-java

mvn sonar:sonar \
  -Dsonar.host.url=http://localhost:8088 \
  -Dsonar.login=admin \
  -Dsonar.password=admin \
  -Dsonar.qualitygate.wait=true
```


## Troubleshooting

### Services not starting?

```bash
# Check logs for specific service
docker-compose logs -f seaj_app

# Restart all services
docker-compose restart

# Full cleanup (removes all volumes and data)
docker-compose down -v
docker-compose up -d --build
```

### Database connection issues?

```bash
# Verify database is running
docker exec seaj_postgres_db psql -U postgres -d seaj_db -c "SELECT 1"

# Should return: (1 row) with value "1"
```

### Kafka not healthy?

```bash
# Check Kafka logs
docker-compose logs seaj_kafka | tail -50
```

## Common Workflows

### Reset Everything 

```bash
docker-compose down -v
docker volume prune -f
docker-compose up -d --build
sleep 180
# Re-seed database
```

### View Application Logs

```bash
# Spring Boot App
docker-compose logs -f seaj_app

# Kafka
docker-compose logs -f seaj_kafka

# All services
docker-compose logs -f
```

### Access Database

```bash
docker exec -it seaj_postgres_db psql -U postgres -d seaj_db
```

### Run Unit Tests

```bash
cd app/backend/backend-java
mvn test
```

## Python Data Pipeline & Dashboard

The Python service (`app/backend/backend-python`) has three parts:

- **ETL pipeline** (`data_processing/`) - fetches instrument data from Yahoo Finance and loads it into the `instruments` table
- **Analysis** (`data_analysis/`) - cleaning, feature engineering and analysis functions, plus exploratory notebooks
- **Dashboard** (`dashboard/`) - a Streamlit app surfacing market insights: risk vs return, best and worst performers, drawdowns and diversification

It uses the same root `.env` as the rest of the platform (`DB_HOST`, `DB_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`). `DB_HOST` defaults to `localhost`.

### Setup

```bash
# From the project root
python3 -m venv .venv
source .venv/bin/activate
pip install -r app/backend/backend-python/requirements.txt
```

### Run the ETL Pipeline

Requires the PostgreSQL container to be running. Instruments already in the database are skipped, so it is safe to re-run.

```bash
cd app/backend/backend-python
python -m data_processing.main
```

### Run the Dashboard

Run from `app/backend/backend-python` so `.streamlit/config.toml` is picked up:

```bash
cd app/backend/backend-python
streamlit run dashboard/app.py
```

Open http://localhost:8501. On a remote VM, forward port 8501 (e.g. the **Ports** tab in VS Code Remote) rather than opening it in the security group. Market data is cached for 6 hours; use **Refresh data** in the sidebar to reload it.

### Run Python Tests

```bash
cd app/backend/backend-python
python -m pytest -v
```
