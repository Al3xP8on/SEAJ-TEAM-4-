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
        booleanParameter(
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
                    echo "=========================================="
                    echo "MANUAL TRIGGER: Database Schema Update"
                    echo "Environment: ${params.DB_ENVIRONMENT}"
                    echo "=========================================="
                    
                    sh '''
                        echo "[Step 1/5] Stopping and removing old database container..."
                        if docker-compose ps -q db > /dev/null 2>&1; then
                            echo "  -> Found running database container, stopping..."
                            docker-compose down -v
                            echo "  -> Old container and volumes removed"
                        else
                            echo "  -> No running database container found"
                        fi
                    '''
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
                    sh '''
                        echo "[Step 2/5] Rebuilding database image with updated schema..."
                        docker-compose build --no-cache db
                        echo "  -> Database image rebuilt successfully"
                    '''
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
                    sh '''
                        echo "[Step 3/5] Starting updated database container..."
                        docker-compose up -d db
                        
                        echo "[Step 4/5] Waiting for database to be ready..."
                        MAX_ATTEMPTS=30
                        ATTEMPT=0
                        while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
                            if docker-compose exec -T db pg_isready -U postgres > /dev/null 2>&1; then
                                echo "  -> Database is ready!"
                                break
                            fi
                            ATTEMPT=$((ATTEMPT + 1))
                            echo "  -> Attempt $ATTEMPT/$MAX_ATTEMPTS - waiting..."
                            sleep 2
                        done
                        
                        if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
                            echo "ERROR: Database failed to start within timeout period"
                            exit 1
                        fi
                    '''
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
                    sh '''
                        echo "[Step 5/5] Verifying database schema update..."
                        echo "  -> Listing databases:"
                        docker-compose exec -T db psql -U postgres -c "\\l"
                        echo "  -> Schema verification complete"
                    '''
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
                dir('starter') {
                    echo "Building application..."
                    sh 'mvn -B clean package -DskipTests'
                    sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
                }
            }
        }
        
        stage('Smoke Test') {
            when {
                expression {
                    return params.UPDATE_DATABASE_SCHEMA == false
                }
            }
            steps {
                sh 'docker run --rm team-skeleton:${BUILD_NUMBER}'
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
                    echo "DATABASE SCHEMA UPDATE SUCCESSFUL"
                    echo "Environment: ${DB_ENVIRONMENT}"
                    echo "Updated on: ${new Date()}"
                    echo "=========================================="
                }
            }
        }
    }
}
