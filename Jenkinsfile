#!groovy
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

def AGENT_LABEL = env.AGENT_LABEL ?: 'ubuntu'
def JDK_NAME = env.JDK_NAME ?: 'JDK 11 (latest)'

pipeline {

    agent {
        label AGENT_LABEL
    }

    environment {
        CI=true
    }

    tools {
        jdk JDK_NAME
    }

    options {
        // Configure an overall timeout for the build of one hour.
        timeout(time: 1, unit: 'HOURS')
        // When we have test-fails e.g. we don't need to run the remaining steps
        skipStagesAfterUnstable()
        buildDiscarder(
                logRotator(artifactNumToKeepStr: '5', numToKeepStr: '10')
        )
        disableConcurrentBuilds()
    }

    stages {
        stage('Build') {
            steps {
                sh "./gradlew clean build -x test"
            }
        }

        stage('Run tests') {
            steps {
                sh "./gradlew build test"
            }
        }
    }

    // Do any post build stuff ... such as sending emails depending on the overall build result.
    post {
        // If this build failed, send an email to the list.
        failure {
            echo "Failed "
        }

        // If this build didn't fail, but there were failing tests, send an email to the list.
        unstable {
            echo "Unstable "
        }

        // Send an email, if the last build was not successful and this one is.
        success {
            echo "Success "
        }

        always {
            echo "Build done"
        }
    }
}
