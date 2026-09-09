// Application Build Pipeline
pipeline {
    agent any
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
        stage('Build Image') {
            steps {
                dir('starter') {
                    sh 'mvn -B clean package -DskipTests'
                    sh 'docker build -t team-skeleton:${BUILD_NUMBER} .'
                }
            }
        }
        stage('Smoke Test') {
            steps {
                sh 'docker run --rm team-skeleton:${BUILD_NUMBER}'
            }
        }
    }
}

// Database Schema Update Pipeline - Triggered separately
pipeline {
    agent any
    parameters {
        choice(name: 'ENVIRONMENT', choices: ['DEMO', 'DEV'], description: 'Select database environment to update')
    }
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Stop DB Container') {
            steps {
                script {
                    sh '''
                        if [ "$(docker ps -aq -f name=seaj_postgres_db)" ]; then
                            echo "Stopping existing database container..."
                            docker-compose down -v
                        else
                            echo "No running container found"
                        fi
                    '''
                }
            }
        }
        stage('Rebuild DB Image') {
            steps {
                script {
                    sh '''
                        echo "Rebuilding database image with updated schema..."
                        docker-compose build --no-cache db
                    '''
                }
            }
        }
        stage('Start Updated DB Container') {
            steps {
                script {
                    sh '''
                        echo "Starting updated database container..."
                        docker-compose up -d db
                        
                        echo "Waiting for database to be ready..."
                        sleep 10
                        
                        if docker-compose exec -T db pg_isready -U postgres > /dev/null 2>&1; then
                            echo "Database is ready!"
                        else
                            echo "Database failed to start"
                            exit 1
                        fi
                    '''
                }
            }
        }
        stage('Verify Schema') {
            steps {
                script {
                    sh '''
                        echo "Verifying database schema update..."
                        docker-compose exec -T db psql -U postgres -d postgres -c "\\l"
                    '''
                }
            }
        }
    }
    post {
        failure {
            script {
                sh '''
                    echo "Pipeline failed! Attempting to restart previous container..."
                    docker-compose down
                '''
            }
        }
    }
}
