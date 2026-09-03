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
