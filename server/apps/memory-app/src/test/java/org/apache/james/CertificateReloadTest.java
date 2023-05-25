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

package org.apache.james;

import static io.restassured.RestAssured.given;
import static org.apache.james.MemoryJamesServerMain.IN_MEMORY_SERVER_AGGREGATE_MODULE;
import static org.apache.james.jmap.JMAPTestingConstants.LOCALHOST_IP;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.james.data.UsersRepositoryModuleChooser;
import org.apache.james.modules.data.MemoryUsersRepositoryModule;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminConfiguration;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableList;

import io.restassured.RestAssured;

class CertificateReloadTest {

    public static class BlindTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {

        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {


        }
    }

    private static final List<String> BASE_CONFIGURATION_FILE_NAMES = ImmutableList.of("dnsservice.xml",
        "dnsservice.xml",
        "imapserver.xml",
        "imapserver2.xml",
        "jwt_publickey",
        "lmtpserver.xml",
        "keystore",
        "mailetcontainer.xml",
        "mailrepositorystore.xml",
        "managesieveserver.xml",
        "pop3server.xml",
        "smtpserver.xml",
        "smtpserver2.xml");

    private GuiceJamesServer jamesServer;
    private TemporaryJamesServer temporaryJamesServer;

    @BeforeEach
    void beforeEach(@TempDir Path workingPath) {
        temporaryJamesServer = new TemporaryJamesServer(workingPath.toFile(), BASE_CONFIGURATION_FILE_NAMES);

        jamesServer = temporaryJamesServer.getJamesServer()
            .combineWith(IN_MEMORY_SERVER_AGGREGATE_MODULE)
            .combineWith(new UsersRepositoryModuleChooser(new MemoryUsersRepositoryModule())
                .chooseModules(UsersRepositoryModuleChooser.Implementation.DEFAULT))
            .overrideWith(binder -> binder.bind(WebAdminConfiguration.class).toInstance(WebAdminConfiguration.TEST_CONFIGURATION));

    }

    @AfterEach
    void afterEach() {
        if (jamesServer != null && jamesServer.isStarted()) {
            jamesServer.stop();
        }
    }

    @Test
    void subjectShouldBeKeptWhenNoRestart() throws Exception {
        temporaryJamesServer.copyResources("smtpserver2.xml", "smtpserver.xml");
        jamesServer.start();

        assertThat(getServerCertificate(jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpSslPort().getValue()).getSubjectX500Principal().getName())
            .isEqualTo("CN=Benoit Tellier,OU=Linagora,O=James,L=Puteaux,ST=Unknown,C=FR");
    }

    private X509Certificate getServerCertificate(int port) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLSocket clientConnection = openSSLConnection(port);

        return Arrays.stream(clientConnection.getSession()
            .getPeerCertificates())
            .filter(X509Certificate.class::isInstance)
            .map(X509Certificate.class::cast)
            .findFirst()
            .orElseThrow();
    }

    private SSLSocket openSSLConnection(int port) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{new BlindTrustManager()}, null);
        SSLSocket clientConnection = (SSLSocket) ctx.getSocketFactory().createSocket(LOCALHOST_IP, port);
        return clientConnection;
    }

    @Test
    void reloadShouldUpdateCertificates() throws Exception {
        temporaryJamesServer.copyResources("smtpserver2.xml", "smtpserver.xml");
        jamesServer.start();

        temporaryJamesServer.copyResources("keystore2", "keystore");

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        int port = jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpSslPort().getValue();
        given()
            .queryParam("reload-certificate")
            .queryParam("port", port)
        .when()
            .post("/servers")
        .then()
            .statusCode(204);

        assertThat(getServerCertificate(port).getSubjectX500Principal().getName())
            .isEqualTo("CN=Testing,OU=Testing,O=Testing,L=Testing,ST=Testing,C=Te");
    }

    @Test
    void reloadShouldUpdateCertificatesForImap() throws Exception {
        temporaryJamesServer.copyResources("imapserver2.xml", "imapserver.xml");
        jamesServer.start();

        temporaryJamesServer.copyResources("keystore2", "keystore");

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        int port = jamesServer.getProbe(ImapGuiceProbe.class).getImapSSLPort();
        given()
            .queryParam("reload-certificate")
            .queryParam("port", port)
        .when()
            .post("/servers")
        .then()
            .statusCode(204);

        assertThat(getServerCertificate(port).getSubjectX500Principal().getName())
            .isEqualTo("CN=Testing,OU=Testing,O=Testing,L=Testing,ST=Testing,C=Te");
    }

    @Test
    void reloadShouldNotAbortExistingConnections() throws Exception {
        temporaryJamesServer.copyResources("smtpserver2.xml", "smtpserver.xml");
        jamesServer.start();
        int port = jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpSslPort().getValue();
        SSLSocket channel = openSSLConnection(port);

        temporaryJamesServer.copyResources("keystore2", "keystore");

        WebAdminGuiceProbe webAdminGuiceProbe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();

        given()
            .queryParam("reload-certificate")
            .queryParam("port", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpSslPort().getValue())
        .when()
            .post("/servers")
        .then()
            .statusCode(204);

        System.out.println(readBytes(channel));
        channel.getOutputStream().write("EHLO toto.com\r\n".getBytes(StandardCharsets.UTF_8));
        assertThat(readBytes(channel))
            .contains("250 8BITMIME");
    }

    private String readBytes(SSLSocket sslSocket) throws IOException {
        byte[] bline = new byte[1024];
        final int read = sslSocket.getInputStream().read(bline);
        return new String(bline, 0, read);
    }

}
