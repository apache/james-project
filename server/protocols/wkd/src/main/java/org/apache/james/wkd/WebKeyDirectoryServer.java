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
package org.apache.james.wkd;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.handler.ssl.SslContextBuilder;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

public class WebKeyDirectoryServer implements Startable {
    private static final Logger LOGGER = LoggerFactory
        .getLogger(WebKeyDirectoryServer.class);
    private static final int RANDOM_PORT = 0;

    private final WebKeyDirectoryConfiguration configuration;
    private final Set<WebKeyDirectoryRoutes> webKeyDirectoryRoutes;
    private Optional<DisposableServer> server;
    private FileSystem fileSystem;
    private String configurationPrefix;

    @Inject
    public WebKeyDirectoryServer(WebKeyDirectoryConfiguration configuration,
        Set<WebKeyDirectoryRoutes> webKeyDirectoryRoutes, FileSystem fileSystem,
        org.apache.james.server.core.configuration.Configuration jamesConfiguration) {
        this.configuration = configuration;
        this.webKeyDirectoryRoutes = webKeyDirectoryRoutes;
        this.server = Optional.empty();
        this.fileSystem = fileSystem;
        this.configurationPrefix = jamesConfiguration == null ? "" : jamesConfiguration.configurationPath();
    }

    public Port getPort() {
        return server.map(DisposableServer::port).map(Port::of)
            .orElseThrow(() -> new IllegalStateException(
                "port is not available because server is not started or disabled"));
    }

    public void start() {
        if (configuration.isEnabled()) {

            SslContextBuilder sslContextBuilder = createSslContextBuilder();

            server = Optional
                .of(HttpServer.create().secure((spec) -> spec.sslContext(sslContextBuilder))
                    .port(configuration.getPort().map(Port::getValue).orElse(RANDOM_PORT))
                    .route(routes -> webKeyDirectoryRoutes
                        .forEach(webKeyDirectoryRoute -> webKeyDirectoryRoute.define(routes)))
                    .wiretap(wireTapEnabled()).bindNow());
        }
    }

    private SslContextBuilder createSslContextBuilder() {
        if(fileSystem == null) {
            LOGGER.error("fileSystem is not specified");
            return null;
        }
        // keytool -genkey -alias wkd -keyalg RSA -validity 1825
        // -keystore "wkd-keystore.jks"
        // -dname "CN=localhost.local,OU=Part,O=Example,L=Berlin,ST=Berlin,C=DE"
        // -ext san=dns:localhost,dns:manuel-XPS-13-9360 -keypass changeit
        // -storepass changeit
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("JKS");

            // Provide location of Java Keystore and password for access
            keyStore.load(
                new FileInputStream(
                    this.fileSystem.getFile(configurationPrefix + configuration.getKeyStore())),
                configuration.getKeyStorePassword().toCharArray());

            // iterate over all aliases
            Enumeration<String> es = keyStore.aliases();
            String alias = "";
            while (es.hasMoreElements()) {
                alias = (String) es.nextElement();
                // if alias refers to a private key break at that point
                // as we want to use that certificate
                if (keyStore.isKeyEntry(alias)) {
                    break;
                }
            }

            KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias,
                new KeyStore.PasswordProtection(configuration.getKeyStorePassword().toCharArray()));

            PrivateKey myPrivateKey = pkEntry.getPrivateKey();

            // Load certificate chain
            Certificate[] chain = keyStore.getCertificateChain(alias);

            SslContextBuilder sslContextBuilder = SslContextBuilder.forServer(myPrivateKey,
                new X509Certificate[] { (X509Certificate) chain[0] });
            return sslContextBuilder;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
            | UnrecoverableEntryException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean wireTapEnabled() {
        return LoggerFactory.getLogger("org.apache.james.wkd.wire").isTraceEnabled();
    }

    @PreDestroy
    public void stop() {
        server.ifPresent(DisposableServer::disposeNow);
    }
}
