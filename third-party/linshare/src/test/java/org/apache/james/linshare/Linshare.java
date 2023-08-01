/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.linshare;

import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.apache.james.util.docker.Images;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class Linshare {
    private static final String WAIT_FOR_BACKEND_INIT_LOG = ".*Server startup.*";
    private static final String WAIT_FOR_LDAP_INIT_LOG = ".*The following user provider for domain '.*' was successfully created.*";
    private static final int LINSHARE_BACKEND_PORT = 8080;
    private static final Logger LOGGER = LoggerFactory.getLogger(Linshare.class);

    private final GenericContainer<?> linshareBackend;
    private final GenericContainer<?> linshareDatabase;
    private final GenericContainer<?> linshareSmtp;
    private final GenericContainer<?> linshareLdap;
    private final GenericContainer<?> linshareMongodb;
    private final GenericContainer<?> linshareDBInit;

    private Network network;

    public Linshare() {
        network = Network.newNetwork();
        linshareDatabase = createDockerDatabase();
        linshareMongodb = createDockerMongodb();
        linshareLdap = createDockerLdap();
        linshareSmtp = createDockerSmtp();
        linshareBackend = createDockerBackend();
        linshareDBInit = createLinshareBackendInit();
    }

    public void start() {
        linshareDatabase.start();
        linshareMongodb.start();
        linshareLdap.start();
        linshareSmtp.start();
        linshareBackend.start();
        linshareDBInit.start();
    }

    public void stop() {
        linshareDBInit.stop();
        linshareDatabase.stop();
        linshareMongodb.stop();
        linshareLdap.stop();
        linshareSmtp.stop();
        linshareBackend.stop();
    }

    public int getPort() {
        return linshareBackend.getMappedPort(LINSHARE_BACKEND_PORT);
    }

    public String getIp() {
        return linshareBackend.getHost();
    }

    public String getUrl() {
        return "http://" + getIp() + ":" + getPort();
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createDockerDatabase() {
        return new GenericContainer<>("linagora/linshare-database:2.3.2")
            .withLogConsumer(frame -> LOGGER.debug("<linshare-database> " + frame.getUtf8String().trim()))
            .withNetworkAliases("database", "linshare_database")
            .withEnv("PGDATA", "/var/lib/postgresql/data/pgdata")
            .withEnv("POSTGRES_USER", "linshare")
            .withEnv("POSTGRES_PASSWORD", "linshare")
            .withNetwork(network);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createDockerMongodb() {
        return new GenericContainer<>("mongo:3.2")
            .withLogConsumer(frame -> LOGGER.debug("<mongo> " + frame.getUtf8String().trim()))
            .withNetworkAliases("mongodb", "linshare_mongodb")
            .withCommand("mongod --smallfiles")
            .withNetwork(network);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createDockerLdap() {
        return new GenericContainer<>("linagora/linshare-ldap-for-tests:1.0.0")
            .withLogConsumer(frame -> LOGGER.debug("<linshare-ldap-for-tests> " + frame.getUtf8String().trim()))
            .withNetworkAliases("ldap")
            .withNetwork(network);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createDockerSmtp() {
        return new GenericContainer<>(Images.FAKE_SMTP)
            .withNetworkAliases("smtp", "linshare_smtp")
            .withNetwork(network);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createDockerBackend() {
        return new GenericContainer<>(
            new ImageFromDockerfile("linshare-backend-"+ UUID.randomUUID().toString())
                .withFileFromClasspath("conf/log4j.properties", "backend/conf/log4j.properties")
                .withFileFromClasspath("conf/catalina.properties", "backend/conf/catalina.properties")
                .withFileFromClasspath("conf/id_rsa", "backend/conf/id_rsa.pri")
                .withFileFromClasspath("conf/id_rsa.pub", "backend/conf/id_rsa.pub")
                .withFileFromClasspath("Dockerfile", "backend/Dockerfile"))
            .withLogConsumer(frame -> LOGGER.debug("<linshare-backend> " + frame.getUtf8String().trim()))
            .withNetworkAliases("backend")
            .withEnv("SMTP_HOST", "linshare_smtp")
            .withEnv("SMTP_PORT", "25")
            .withEnv("POSTGRES_HOST", "linshare_database")
            .withEnv("POSTGRES_PORT", "5432")
            .withEnv("POSTGRES_USER", "linshare")
            .withEnv("POSTGRES_PASSWORD", "linshare")
            .withEnv("MONGODB_HOST", "linshare_mongodb")
            .withEnv("MONGODB_PORT", "27017")
            .withEnv("THUMBNAIL_ENABLE", "false")
            .withExposedPorts(LINSHARE_BACKEND_PORT)
            .waitingFor(Wait.forLogMessage(WAIT_FOR_BACKEND_INIT_LOG, 1)
                .withStartupTimeout(Duration.ofMinutes(10)))
            .withNetwork(network);
    }

    @SuppressWarnings("resource")
    private GenericContainer<?> createLinshareBackendInit() {
        return new GenericContainer<>("linagora/linshare-init:2.3.2")
            .withNetworkAliases("init")
            .withLogConsumer(frame -> LOGGER.debug("<linshare-init> " + frame.getUtf8String().trim()))
            .withEnv("LS_HOST", "backend")
            .withEnv("LS_PORT", "8080")
            .withEnv("LS_LDAP_NAME", "ldap-local")
            .withEnv("LS_LDAP_URL", "ldap://ldap:389")
            .withEnv("LS_LDAP_BASE_DN", "ou=People,dc=linshare,dc=org")
            .withEnv("LS_LDAP_DN", "cn=linshare,dc=linshare,dc=org")
            .withEnv("LS_LDAP_PW", "linshare")
            .withEnv("LS_DOMAIN_PATTERN_NAME", "openldap-local")
            .withEnv("LS_DOMAIN_PATTERN_MODEL", "868400c0-c12e-456a-8c3c-19e985290586")
            .withEnv("NO_REPLY_ADDRESS", "linshare-noreply@linshare.org")
            .withEnv("DEBUG", "1")
            .withEnv("FORCE_INIT", "1")
            .waitingFor(Wait.forLogMessage(WAIT_FOR_LDAP_INIT_LOG, 1)
                .withStartupTimeout(Duration.ofMinutes(10)))
            .withNetwork(network);
    }

    public RequestSpecification fakeSmtpRequestSpecification() {
        return new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(linshareSmtp.getMappedPort(80))
            .setBaseUri("http://" + linshareSmtp.getHost())
            .build();
    }
}
