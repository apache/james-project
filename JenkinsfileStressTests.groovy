// This file should respect the Jenkinsfile format describe here: 
// https://jenkins.io/doc/book/pipeline/jenkinsfile/
// 
// It may be used by any Jenkins instance you own.
//
// In order to work properly, it requires the following parameters:
// - SCENARIO: the Gatling scenario you want to play (ex. com.linagora.gatling.imap.scenario.ImapSimpleScenario)

// Method in order to retry a command.
// It may be used to wait for a service to be available.

pipeline {
    options {
        disableConcurrentBuilds()
    }

    agent none

    stages {
        stage('Prepare target') {
            agent {
                node {
                    label 'target'
                }
            }

            tools {
                maven 'maven'
            }

            stages {
                stage('Compile') {
                    steps {
                        sh "mvn clean install -T1C -DskipTests -Dmaven.javadoc.skip=true"
                    }
                }
                stage('Build image') {
                    steps {
                        script {
                            sh "cp server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.jar dockerfiles/run/guice/cassandra-rabbitmq/destination"
                            sh "cp -r server/container/guice/cassandra-rabbitmq-guice/target/james-server-cassandra-rabbitmq-guice.lib dockerfiles/run/guice/cassandra-rabbitmq/destination"
                            sh "cp server/container/cli/target/james-server-cli.jar dockerfiles/run/guice/cassandra-rabbitmq/destination"
                            sh "cp -r server/container/cli/target/james-server-cli.lib dockerfiles/run/guice/cassandra-rabbitmq/destination"
                            sh 'cp server/protocols/jmap-draft-integration-testing/rabbitmq-jmap-draft-integration-testing/src/test/resources/keystore dockerfiles/run/guice/cassandra-rabbitmq/destination/conf'
                            sh 'wget -O dockerfiles/run/guice/cassandra-rabbitmq/destination/glowroot.zip https://github.com/glowroot/glowroot/releases/download/v0.13.4/glowroot-0.13.4-dist.zip && unzip -u dockerfiles/run/guice/cassandra-rabbitmq/destination/glowroot.zip -d dockerfiles/run/guice/cassandra-rabbitmq/destination'

                            if (params.PROFILE in ["s3", "swift"]) {
                                sh 'cp benchmarks/' + params.PROFILE + '.properties dockerfiles/run/guice/cassandra-rabbitmq/destination/conf/blob.properties'
                            }

                            sh 'docker build -t james_run dockerfiles/run/guice/cassandra-rabbitmq'
                        }
                    }
                }
                stage('Start James') {
                    steps {
                        script {
                            sh 'docker rm -f cassandra rabbitmq elasticsearch tika swift james_run || true'
                            if (fileExists('/srv/bench-running-docker')) {
                                echo 'Last build failed, cleaning provisionning'
                                sh 'sudo btrfs subvolume delete /srv/bench-running-docker'
                            }
                            switch (params.PROFILE) {
                                case "reference":
                                    sh "cd /srv && sudo btrfs subvolume snapshot bench-snapshot bench-running-docker"
                                    sh 'docker run -d --name=cassandra -p 9042:9042 -v /srv/bench-running-docker/cassandra:/var/lib/cassandra cassandra:3.11.3'
                                    sh 'docker run -d --name=elasticsearch -p 9200:9200 -v /srv/bench-running-docker/elasticsearch:/usr/share/elasticsearch/data/elasticsearch  --env "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.3.2'
                                    sh 'docker run -d --name=tika apache/tika:1.24'
                                    sh 'docker run -d --name=swift -p 8080:8080 -v /srv/bench-running-docker/swift:/srv/1/node/sdb1 jeantil/openstack-keystone-swift:pike'
                                    sh 'docker run -d --name=rabbitmq -p 15672:15672 -p 5672:5672 rabbitmq:3.8.1-management'

                                    sh 'docker run -d --hostname HOSTNAME -p 25:25 -p 1080:80 -p 8000:8000 -p 110:110 -p 143:143 -p 465:465 -p 587:587 -p 993:993 --link cassandra:cassandra --link rabbitmq:rabbitmq --link elasticsearch:elasticsearch --link tika:tika --link swift:swift --name james_run -t james_run'
                                    break
                                case ["s3", "swift"]:
                                    sh 'docker run -d --name=cassandra -p 9042:9042 cassandra:3.11.3'
                                    sh 'docker run -d --name=elasticsearch -p 9200:9200 --env "discovery.type=single-node" docker.elastic.co/elasticsearch/elasticsearch:6.3.2'
                                    sh 'docker run -d --name=tika apache/tika:1.24'
                                    sh 'docker run -d --name=rabbitmq -p 15672:15672 -p 5672:5672 rabbitmq:3.8.1-management'

                                    sh 'printenv | grep OS_ > env.file'
                                    sh 'docker run -d --env-file env.file --hostname HOSTNAME -p 25:25 -p 1080:80 -p 8000:8000 -p 110:110 -p 143:143 -p 465:465 -p 587:587 -p 993:993 --link cassandra:cassandra --link rabbitmq:rabbitmq --link elasticsearch:elasticsearch --link tika:tika --name james_run -t james_run'
                                    break
                            }
                            def jamesCliWithOptions = 'java -jar /root/james-cli.jar -h 127.0.0.1 -p 9999'
                            timeout(time: 20, unit: 'MINUTES') {
                                retry(200) {
                                    sleep 5
                                    sh "docker exec james_run ${jamesCliWithOptions} listusers"
                                }
                            }
                            if (params.PROFILE in ["s3", "swift"]) {
                                sh "docker exec james_run ${jamesCliWithOptions} removedomain localhost || true"
                                sh "docker exec james_run ${jamesCliWithOptions} removedomain james.linagora.com || true"
                                sh "docker exec james_run ${jamesCliWithOptions} adddomain open-paas.org"
                                for (int n = 0; n <= 100; n++) {
                                    sh "docker exec james_run ${jamesCliWithOptions} adduser user${n}@open-paas.org secret"
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Start injector') {
            agent {
                node {
                    label 'injector'
                }
            }
            stages {
                stage('Run Gatling test') {
                    steps {
                        build job: 'Gatling-job', parameters: [[$class: 'StringParameterValue', name: 'SBT_ACTION', value: "gatling:testOnly ${params.SIMULATION}"], [$class: 'StringParameterValue', name: 'GITHUB', value: params.GITHUB_SIMULATIONS]]
                    }
                }
            }
        }
    }

    post {
        always {
            node('target') {
                script {
                    sh 'docker logs james_run || true'
                    sh 'docker rm -f cassandra rabbitmq elasticsearch tika swift james_run || true'
                    sh 'sudo btrfs subvolume delete /srv/bench-running-docker || true'
                }
            }
        }
    }
}
