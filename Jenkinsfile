// Database Schema Update Pipeline
// Single-purpose: Update PostgreSQL database schema from organized table files
pipeline {
    agent any
    
    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timestamps()
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
        
        stage('Stop & Clean Old Database') {
            steps {
                script {
                    echo "=========================================="
                    echo "DATABASE SCHEMA UPDATE"
                    echo "=========================================="
                    echo ""
                    echo "[Step 1/5] Stopping and removing old database container..."
                    
                    sh """
                        docker-compose down --remove-orphans 2>/dev/null || true
                        echo "  -> Killing any running containers..."
                        docker kill seaj_postgres_db 2>/dev/null || true
                        echo "  -> Force removing container..."
                        docker rm -f seaj_postgres_db 2>/dev/null || true
                        echo "  -> Removing PostgreSQL data volume..."
                        docker volume rm -f pgdata_volume 2>/dev/null || true
                        echo "  -> Pruning unused Docker resources..."
                        docker system prune -af --volumes 2>/dev/null || true
                        echo "  ✓ Old container and volumes removed"
                    """
                }
            }
        }
        
        stage('Rebuild Database Image') {
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
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            echo "[Step 2/5] Rebuilding database image with updated schema..."
                            sh """
                                docker-compose build --no-cache db
                                echo "  ✓ Database image rebuilt successfully"
                            """
                        }
                    }
                }
            }
        }
        
        stage('Start Database Container') {
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
                            "SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/SEAJ_db_DEMO",
                            "SPRING_DATASOURCE_USERNAME=${DB_USERNAME}",
                            "SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
                        ]) {
                            echo "[Step 3/5] Starting database container..."
                            sh """
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
                                        echo "  ✓ Database is ready!"
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
        
        stage('Verify Schema') {
            steps {
                script {
                    withCredentials([
                        string(credentialsId: 'postgres_password', variable: 'DB_PASSWORD'),
                        string(credentialsId: 'postgres_username', variable: 'DB_USERNAME')
                    ]) {
                        withEnv([
                            "POSTGRES_DB=SEAJ_db_DEMO",
                            "POSTGRES_PASSWORD=${DB_PASSWORD}",
                            "DB_USERNAME=${DB_USERNAME}"
                        ]) {
                            echo "[Step 5/5] Verifying database schema..."
                            sh """
                                echo ""
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
    }
    
    post {
        failure {
            script {
                echo "=========================================="
                echo "❌ DATABASE UPDATE FAILED"
                echo "=========================================="
                sh '''
                    echo "Attempting to bring down containers..."
                    docker-compose down 2>/dev/null || true
                '''
            }
        }
        success {
            script {
                def timestamp = new Date().format('yyyy-MM-dd HH:mm:ss')
                echo ""
                echo "=========================================="
                echo "✅ DATABASE SCHEMA UPDATE SUCCESSFUL"
                echo "=========================================="
                echo ""
                echo "Updated: ${timestamp}"
                echo "Build: #${BUILD_NUMBER}"
                echo ""
                echo "Database Schema Organization:"
                echo "  📁 Shared Tables"
                echo "     - clients"
                echo "     - instruments"
                echo ""
                echo "  📁 Account Service"
                echo "     - accounts"
                echo ""
                echo "  📁 Order Service"
                echo "     - orders"
                echo "     - order_history"
                echo ""
                echo "  📁 Positions Service"
                echo "     - positions"
                echo "     - price_history"
                echo "     - current_prices"
                echo ""
                echo "Table Files:"
                echo "  Location: infra/db/tables/"
                echo "  ├── shared/"
                echo "  ├── account-service/"
                echo "  ├── order-service/"
                echo "  ├── positions-service/"
                echo "  └── index.sql"
                echo ""
                echo "=========================================="
            }
        }
    }
}
