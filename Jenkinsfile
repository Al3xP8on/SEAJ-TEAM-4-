// Multi-branch Pipeline with Manual Database Schema Update Trigger
pipeline {
    agent any
    
    options {
        // Keep builds to manage disk space
        buildDiscarder(logRotator(numToKeepStr: '10'))
        // Add timestamps to console output
        timestamps()
    }
    
    parameters {
        booleanParam(
            name: 'UPDATE_DATABASE_SCHEMA',
            defaultValue: false,
            description: 'Enable to manually trigger database schema update (removes old container and spins up new one)'
        )
        choice(
            name: 'DB_ENVIRONMENT',
            choices: ['DEMO', 'DEV'],
            description: 'Database environment to update (only used if UPDATE_DATABASE_SCHEMA is true)'
        )
    }
    
    tools {
        jdk 'JDK21'
        maven 'maven'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Update Database Schema') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == true
                }
            }
            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'postgres_password', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'postgres_username', variable: 'DB_USERNAME')
                    ]) {
                        withEnv([
                            "POSTGRES_DB=SEAJ_db_DEMO",
                            "POSTGRES_PASSWORD=${DB_PASSWORD}",
                            "DB_USERNAME=${DB_USERNAME}",
                            "DB_PORT=5432",
                            "APP_PORT=8081",
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            echo "=========================================="
                            echo "MANUAL TRIGGER: Database Schema Update"
                            echo "Environment: ${params.DB_ENVIRONMENT}"
                            echo "=========================================="
                            
                            sh """
                                echo "[Step 1/5] Stopping and removing old database container..."
                                docker-compose down --remove-orphans 2>/dev/null || true
                                echo "  -> Killing any running containers..."
                                docker kill seaj_postgres_db 2>/dev/null || true
                                echo "  -> Force removing container..."
                                docker rm -f seaj_postgres_db 2>/dev/null || true
                                echo "  -> Removing PostgreSQL data volume with force flag..."
                                docker volume rm -f ipe_feature_container-automation_pgdata_volume 2>/dev/null || true
                                
                                echo "  -> Cleaning volume directory from inside container..."
                                docker run --rm -v ipe_feature_container-automation_pgdata_volume:/pgdata alpine:latest sh -c 'rm -rf /pgdata/* /pgdata/.*' 2>/dev/null || true
                                
                                echo "  -> Pruning unused Docker resources..."
                                docker system prune -af --volumes 2>/dev/null || true
                                echo "  -> Old container and volumes removed"
                            """
                        }
                    }
                }
            }
        }
        
        stage('Rebuild Database Image') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == true
                }
            }
            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'postgres_password', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'postgres_username', variable: 'DB_USERNAME')
                    ]) {
                        withEnv([
                            "POSTGRES_DB=SEAJ_db_DEMO",
                            "POSTGRES_PASSWORD=${DB_PASSWORD}",
                            "DB_USERNAME=${DB_USERNAME}",
                            "DB_PORT=5432",
                            "APP_PORT=8081",
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            sh """
                                echo "[Step 2/5] Rebuilding database image with updated schema..."
                                docker-compose build --no-cache db
                                echo "  -> Database image rebuilt successfully"
                            """
                        }
                    }
                }
            }
        }
        
        stage('Start Updated Database Container') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == true
                }
            }
            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'postgres_password', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'postgres_username', variable: 'DB_USERNAME')
                    ]) {
                        withEnv([
                            "POSTGRES_DB=SEAJ_db_DEMO",
                            "POSTGRES_PASSWORD=${DB_PASSWORD}",
                            "DB_USERNAME=${DB_USERNAME}",
                            "DB_PORT=5432",
                            "APP_PORT=8081",
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            sh """
                                echo "[Step 3/5] Starting updated database container..."
                                docker-compose up -d db
                                
                                echo "[Step 4/5] Waiting for database to be ready..."
                                sleep 10
                                
                                echo "  -> Checking container logs..."
                                docker-compose logs db
                                
                                echo "  -> Testing database connection..."
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT 1" || echo "Connection failed"
                                
                                MAX_ATTEMPTS=30
                                ATTEMPT=0
                                while [ \$ATTEMPT -lt \$MAX_ATTEMPTS ]; do
                                    if docker-compose exec -T db pg_isready -U \$DB_USERNAME > /dev/null 2>&1; then
                                        echo "  -> Database is ready!"
                                        break
                                    fi
                                    ATTEMPT=\$((ATTEMPT + 1))
                                    echo "  -> Attempt \$ATTEMPT/\$MAX_ATTEMPTS - waiting..."
                                    sleep 2
                                done
                                
                                if [ \$ATTEMPT -eq \$MAX_ATTEMPTS ]; then
                                    echo "ERROR: Database failed to start"
                                    docker-compose logs db
                                    exit 1
                                fi
                            """
                        }
                    }
                }
            }
        }
        
        stage('Verify Schema Update') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == true
                }
            }
            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'postgres_password', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'postgres_username', variable: 'DB_USERNAME')
                    ]) {
                        withEnv([
                            "POSTGRES_DB=SEAJ_db_DEMO",
                            "POSTGRES_PASSWORD=${DB_PASSWORD}",
                            "DB_USERNAME=${DB_USERNAME}",
                            "DB_PORT=5432",
                            "APP_PORT=8081",
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            sh """
                                echo "[Step 5/5] Verifying database schema update..."
                                
                                echo "=========================================="
                                echo "Database Verification Report"
                                echo "=========================================="
                                
                                echo ""
                                echo "1. Databases:"
                                docker-compose exec -T db psql -U \$DB_USERNAME -c "\\l" | grep -E "SEAJ_db"
                                
                                echo ""
                                echo "2. Shared Tables (Reference Data):"
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ('clients', 'instruments')" || echo "  -> None found"
                                
                                echo ""
                                echo "3. Account Service Tables:"
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ('accounts')" || echo "  -> None found"
                                
                                echo ""
                                echo "4. Order Service Tables:"
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ('orders', 'order_history')" || echo "  -> None found"
                                
                                echo ""
                                echo "5. Positions Service Tables:"
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ('positions', 'price_history', 'current_prices')" || echo "  -> None found"
                                
                                echo ""
                                echo "6. All Tables Summary:"
                                docker-compose exec -T db psql -U \$DB_USERNAME -d \$POSTGRES_DB -c "SELECT COUNT(*) as total_tables FROM pg_tables WHERE schemaname='public'"
                                
                                echo ""
                                echo "=========================================="
                                echo "Schema verification complete"
                                echo "=========================================="
                            """
                        }
                    }
                }
            }
        }
        
        stage('Build Application') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == false
                }
            }
            steps {
                dir('app/backend') {
                    echo "Building SEAJ Trading Platform..."
                    sh 'mvn -B clean package -DskipTests'
                }
            }
        }
        
        stage('Build Docker Image') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == false
                }
            }
            steps {
                echo "Building Docker image..."
                sh 'docker build -t seaj-trading-platform:${BUILD_NUMBER} -f app/backend/Dockerfile .'
                sh 'docker tag seaj-trading-platform:${BUILD_NUMBER} seaj-trading-platform:latest'
            }
        }
        
        stage('Smoke Test') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == false
                }
            }
            steps {
                echo "Running smoke tests..."
                sh '''
                    echo "Starting application for smoke tests..."
                    docker-compose up -d db app
                    
                    echo "Waiting for application to start (20 seconds)..."
                    sleep 20
                    
                    echo "Testing API Gateway health endpoint..."
                    curl -f http://localhost:8080/api/gateway/health || exit 1
                    
                    echo "Testing Account Service endpoint..."
                    curl -f http://localhost:8080/api/accounts || exit 1
                    
                    echo "Testing Order Service endpoint..."
                    curl -f http://localhost:8080/api/orders || exit 1
                    
                    echo "Testing Positions Service endpoint..."
                    curl -f http://localhost:8080/api/positions || exit 1
                    
                    echo "All smoke tests passed!"
                    
                    echo "Stopping test containers..."
                    docker-compose down
                '''
            }
        }
    }
    
    post {
        failure {
            script {
                if (params.UPDATE_DATABASE_SCHEMA) {
                    echo "=========================================="
                    echo "DATABASE UPDATE FAILED"
                    echo "=========================================="
                    sh '''
                        echo "Attempting to bring down containers..."
                        docker-compose down 2>/dev/null || true
                    '''
                }
            }
        }
        success {
            script {
                if (params.UPDATE_DATABASE_SCHEMA) {
                    echo "=========================================="
                    echo "✅ DATABASE SCHEMA UPDATE SUCCESSFUL"
                    echo "=========================================="
                    echo ""
                    echo "Environment: ${params.DB_ENVIRONMENT}"
                    echo "Updated on: $(date)"
                    echo ""
                    echo "Database Schema Organization:"
                    echo "  📁 Shared Tables (Reference Data)"
                    echo "     - clients"
                    echo "     - instruments"
                    echo ""
                    echo "  📁 Account Service Tables"
                    echo "     - accounts"
                    echo ""
                    echo "  📁 Order Service Tables"
                    echo "     - orders"
                    echo "     - order_history"
                    echo ""
                    echo "  📁 Positions Service Tables"
                    echo "     - positions"
                    echo "     - price_history"
                    echo "     - current_prices"
                    echo ""
                    echo "Table Files Location:"
                    echo "  infra/db/tables/"
                    echo "  ├── shared/                    (Reference data)"
                    echo "  ├── account-service/           (Accounts)"
                    echo "  ├── order-service/             (Orders)"
                    echo "  ├── positions-service/         (Positions)"
                    echo "  └── index.sql                  (Indices)"
                    echo ""
                    echo "Schema Consolidation:"
                    echo "  - SEAJ_db_DEMO.sql (Demo environment)"
                    echo "  - SEAJ_db_DEV.sql  (Dev environment)"
                    echo ""
                    echo "=========================================="
                }
            }
        }
        always {
            script {
                if (params.UPDATE_DATABASE_SCHEMA == false) {
                    echo "=========================================="
                    echo "📊 Build Summary"
                    echo "=========================================="
                    echo "Build: seaj-trading-platform:${BUILD_NUMBER}"
                    echo "Application: SEAJ Trading Platform"
                    echo "Services: Account, Order, Positions"
                    echo "Database: PostgreSQL (Shared)"
                    echo "=========================================="
                }
            }
        }
    }
}
