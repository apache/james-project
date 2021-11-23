#!groovy

/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

def AGENT_LABEL = env.AGENT_LABEL ?: 'ubuntu'
def JDK_NAME = env.JDK_NAME ?: 'jdk_11_latest'

pipeline {

    agent {
        node {
            label AGENT_LABEL
        }
    }

    environment {
        // ... setup any environment variables ...
        BUILD_ID = UUID.randomUUID().toString()
        MVN_LOCAL_REPO_OPT = '-Dmaven.repo.local=.repository'
        MVN_TEST_FAIL_IGNORE = '-Dmaven.test.failure.ignore=true'
        MVN_SHOW_TIMESTAMPS="-Dorg.slf4j.simpleLogger.showDateTime=true -Dorg.slf4j.simpleLogger.dateTimeFormat=HH:mm:ss,SSS"
        CI = true
        LC_CTYPE = 'en_US.UTF-8'
    }

    tools {
        // ... tell Jenkins what java version, maven version or other tools are required ...
        maven 'maven_3_latest'
        jdk JDK_NAME
    }

    options {
        // Configure an overall timeout for the build of 4 hours.
        timeout(time: 10, unit: 'HOURS')
        // When we have test-fails e.g. we don't need to run the remaining steps
        skipStagesAfterUnstable()
        buildDiscarder(
                logRotator(artifactNumToKeepStr: '10', numToKeepStr: '30')
        )
        disableConcurrentBuilds()
    }

    triggers {
        issueCommentTrigger('.*test this please.*')
    }

    stages {
        stage('Initialization') {
            steps {
                echo 'Building branch ' + env.BRANCH_NAME
                echo 'Using PATH ' + env.PATH
            }
        }

        stage('Cleanup') {
            steps {
                echo 'Cleaning up the workspace'
                deleteDir()
                echo 'Cleaning up James maven repo dependencies'
                sh 'rm -rf /home/jenkins/.m2/repository/org/apache/james'
            }
        }

        stage('Checkout') {
            steps {
                echo 'Checking out branch ' + env.BRANCH_NAME
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Building'
                sh 'mvn -U -B -e clean install -DskipTests -T1C ${MVN_SHOW_TIMESTAMPS}'
            }
        }

        stage('Stable Tests') {
            steps {
                echo 'Running tests'
                sh 'mvn -B -e -fae test ${MVN_SHOW_TIMESTAMPS} -P ci-test'
            }
            post {
                always {
                    junit(testResults: '**/surefire-reports/*.xml', allowEmptyResults: false)
                    junit(testResults: '**/failsafe-reports/*.xml', allowEmptyResults: true)
                }
                failure {
                    archiveArtifacts artifacts: '**/target/test-run.log' , fingerprint: true
                    archiveArtifacts artifacts: '**/surefire-reports/*' , fingerprint: true
                }
            }
        }

        stage('Unstable Tests') {
            steps {
                echo 'Running unstable tests'
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    sh 'mvn -B -e -fae test -Punstable-tests ${MVN_SHOW_TIMESTAMPS} -P ci-test'
                }
            }
            post {
                always {
                    junit(testResults: '**/surefire-reports/*.xml', allowEmptyResults: true)
                    junit(testResults: '**/failsafe-reports/*.xml', allowEmptyResults: true)
                }
                failure {
                    archiveArtifacts artifacts: '**/target/test-run.log' , fingerprint: true
                    archiveArtifacts artifacts: '**/surefire-reports/*' , fingerprint: true
                }
            }
        }

        stage('Deploy') {
            when { branch 'master' }
            steps {
                echo 'Deploying'
                sh 'mvn -B -e deploy -Pdeploy -DskipTests -T1C'
            }
        }
   }
// Do any post build stuff ... such as sending emails depending on the overall build result.
    post {
        // If this build failed, send an email to the list.
        failure {
            script {
                if (env.BRANCH_NAME == "master") {
                    emailext(
                            subject: "[BUILD-FAILURE]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                            body: """
BUILD-FAILURE: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]':
Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]</a>"
""",
                            to: "server-dev@james.apache.org",
                            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                    )
                }else{
                    emailext(
                            subject: "[BUILD-FAILURE]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                            body: """
BUILD-FAILURE: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]':
Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]</a>"
""",
                            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                    )
                }
            }
        }

        // If this build didn't fail, but there were failing tests, send an email to the list.
        unstable {
            script {
                if (env.BRANCH_NAME == "master") {
                    emailext(
                            subject: "[BUILD-UNSTABLE]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                            body: """
BUILD-UNSTABLE: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]':
Check console output at "<a href="${env.BUILD_URL}">${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]</a>"
""",
                            to: "server-dev@james.apache.org",
                            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                    )
                }
            }
        }

        // Send an email, if the last build was not successful and this one is.
        success {
            // Cleanup the build directory if the build was successful
            // (in this cae we probably don't have to do any post-build analysis)
            deleteDir()
            script {
                if (env.BRANCH_NAME == "master" && (currentBuild.previousBuild != null) && (currentBuild.previousBuild.result != 'SUCCESS')) {
                    emailext(
                            subject: "[BUILD-STABLE]: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]'",
                            body: """
BUILD-STABLE: Job '${env.JOB_NAME} [${env.BRANCH_NAME}] [${env.BUILD_NUMBER}]':
Is back to normal.
""",
                            to: "server-dev@james.apache.org",
                            recipientProviders: [[$class: 'DevelopersRecipientProvider']]
                    )
                }
            }
        }
    }
}
