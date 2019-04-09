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

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

public class Linshare {
    private static final String WAIT_FOR_BACKEND_INIT_LOG = ".*Server startup.*";
    private static final String WAIT_FOR_DB_INIT_LOG = ".*/linshare/webservice/rest/admin/authentication/change_password.*";
    private static final int LINSHARE_BACKEND_PORT = 8080;

    private final GenericContainer<?> linshareBackend;
    private final GenericContainer<?> linshareDatabase;
    private final GenericContainer<?> linshareDatabaseInit;
    private final GenericContainer<?> linshareSmtp;
    private final GenericContainer<?> linshareLdap;
    private final GenericContainer<?> linshareMongodb;

    private Network network;

    @SuppressWarnings("resource")
    public Linshare() {
        network = Network.newNetwork();
        linshareDatabase = createDockerDatabase();
        linshareMongodb = createDockerMongodb();
        linshareLdap = createDockerLdap();
        linshareSmtp = createDockerSmtp();
        linshareBackend = createDockerBackend();
        linshareDatabaseInit = createDockerDatabaseInit();
    }

    public void start() {
        linshareDatabase.start();
        linshareMongodb.start();
        linshareLdap.start();
        linshareSmtp.start();
        linshareBackend.start();
        linshareDatabaseInit.start();
    }

    public void stop() {
        linshareDatabase.stop();
        linshareMongodb.stop();
        linshareLdap.stop();
        linshareSmtp.stop();
        linshareBackend.stop();
        linshareDatabaseInit.stop();
    }

    public int getPort() {
        return linshareBackend.getMappedPort(LINSHARE_BACKEND_PORT);
    }

    public String getIp() {
        return linshareBackend.getContainerIpAddress();
    }

    private GenericContainer createDockerDatabase() {
        return new GenericContainer<>("linagora/linshare-database:2.2")
            .withNetworkAliases("database", "linshare_database")
            .withEnv("PGDATA", "/var/lib/postgresql/data/pgdata")
            .withEnv("POSTGRES_USER", "linshare")
            .withEnv("POSTGRES_PASSWORD", "linshare")
            .withNetwork(network);
    }

    private GenericContainer createDockerMongodb() {
        return new GenericContainer<>("mongo:3.2")
            .withNetworkAliases("mongodb", "linshare_mongodb")
            .withCommand("mongod --smallfiles")
            .withNetwork(network);
    }

    private GenericContainer createDockerLdap() {
        return new GenericContainer<>("linagora/linshare-ldap-for-tests:1.0")
            .withNetworkAliases("ldap")
            .withNetwork(network);
    }

    private GenericContainer createDockerSmtp() {
        return new GenericContainer<>("linagora/opensmtpd")
            .withNetworkAliases("smtp", "linshare_smtp")
            .withClasspathResourceMapping("./conf/smtpd.conf",
                "/etc/smtpd/smtpd.conf",
                BindMode.READ_ONLY)
            .withNetwork(network);
    }

    private GenericContainer createDockerDatabaseInit() {
        return new GenericContainer<>("chibenwa/linshare-database-init:2.2")
            .withEnv("TOMCAT_HOST", "backend")
            .withEnv("TOMCAT_PORT", "8080")
            .withEnv("TOMCAT_LDAP_NAME", "ldap-local")
            .withEnv("TOMCAT_LDAP_URL", "ldap://ldap:389")
            .withEnv("TOMCAT_LDAP_BASE_DN", "ou=People,dc=linshare,dc=org")
            .withEnv("TOMCAT_LDAP_DN", "cn=linshare,dc=linshare,dc=org")
            .withEnv("TOMCAT_LDAP_PW", "linshare")
            .withEnv("TOMCAT_DOMAIN_PATTERN_NAME", "openldap-local")
            .withEnv("TOMCAT_DOMAIN_PATTERN_MODEL", "868400c0-c12e-456a-8c3c-19e985290586")
            .withEnv("NO_REPLY_ADDRESS", "linshare-noreply@linshare.org")
            .withEnv("DEBUG", "1")
            .withNetwork(network)
            .waitingFor(Wait.forLogMessage(WAIT_FOR_DB_INIT_LOG, 1));
    }

    private GenericContainer createDockerBackend() {
        return new GenericContainer<>("linagora/linshare-backend:2.2")
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
            .withClasspathResourceMapping("./conf/catalina.properties",
                "/usr/local/tomcat/conf/catalina.properties",
                BindMode.READ_ONLY)
            .withClasspathResourceMapping("./conf/log4j.properties",
                "/etc/linshare/log4j.properties",
                BindMode.READ_ONLY)
            .withClasspathResourceMapping("./ssl/id_rsa",
                "/etc/linshare/id_rsa",
                BindMode.READ_ONLY)
            .withClasspathResourceMapping("./ssl/id_rsa.pub",
                "/etc/linshare/id_rsa.pub",
                BindMode.READ_ONLY)
            .withExposedPorts(LINSHARE_BACKEND_PORT)
            .waitingFor(Wait.forLogMessage(WAIT_FOR_BACKEND_INIT_LOG, 1))
            .withNetwork(network);
    }
}
