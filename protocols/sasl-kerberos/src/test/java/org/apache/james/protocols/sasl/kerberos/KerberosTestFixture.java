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

package org.apache.james.protocols.sasl.kerberos;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.kerby.kerberos.kerb.server.SimpleKdcServer;

public class KerberosTestFixture implements AutoCloseable {
    public record Service(String serviceName, String serverName, String principal, Path keyTab) {
    }

    public static final String REALM = "JAMES.TEST";
    public static final String USER_PRINCIPAL = "alice@" + REALM;
    public static final String KRB5_CONFIGURATION_RESOURCE = "java.security.krb5.conf";

    private final Path workDirectory;
    private final SimpleKdcServer kdcServer;
    private final Path userKeyTab;
    private final String previousKrb5Configuration;

    public KerberosTestFixture(Path workDirectory) throws Exception {
        this.workDirectory = workDirectory;
        this.previousKrb5Configuration = System.getProperty(KRB5_CONFIGURATION_RESOURCE);
        this.kdcServer = startKdc(workDirectory);
        this.userKeyTab = workDirectory.resolve("alice.keytab");
        kdcServer.createPrincipal(USER_PRINCIPAL);
        kdcServer.exportPrincipal(USER_PRINCIPAL, userKeyTab.toFile());
    }

    public Service provisionService(String serviceName, String serverName) throws Exception {
        String principal = serviceName + "/" + serverName + "@" + REALM;
        Path keyTab = workDirectory.resolve(serviceName + ".keytab");
        kdcServer.createPrincipal(principal);
        kdcServer.exportPrincipal(principal, keyTab.toFile());
        return new Service(serviceName, serverName, principal, keyTab);
    }

    public GssapiTestClient client(Service service) throws Exception {
        return client(service, Optional.empty());
    }

    public GssapiTestClient client(Service service, Optional<String> authorizationId) throws Exception {
        return new GssapiTestClient(USER_PRINCIPAL, userKeyTab, service.serviceName(), service.serverName(), authorizationId);
    }

    @Override
    public void close() throws Exception {
        try {
            kdcServer.stop();
        } finally {
            if (previousKrb5Configuration == null) {
                System.clearProperty(KRB5_CONFIGURATION_RESOURCE);
            } else {
                System.setProperty(KRB5_CONFIGURATION_RESOURCE, previousKrb5Configuration);
            }
        }
    }

    private static SimpleKdcServer startKdc(Path workDirectory) throws Exception {
        SimpleKdcServer kdcServer = new SimpleKdcServer();
        kdcServer.setWorkDir(workDirectory.toFile());
        kdcServer.setKdcRealm(REALM);
        kdcServer.setKdcHost("127.0.0.1");
        kdcServer.setAllowUdp(false);
        kdcServer.setKdcTcpPort(availablePort());
        kdcServer.init();
        kdcServer.start();
        return kdcServer;
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
