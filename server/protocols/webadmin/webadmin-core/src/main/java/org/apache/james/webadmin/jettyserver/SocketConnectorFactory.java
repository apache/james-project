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

package org.apache.james.webadmin.jettyserver;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import spark.ssl.SslStores;
import spark.utils.Assert;

/**
 * Fork from spark.embeddedserver.jetty.SocketConnectorFactory
 * Creates socket connectors.
 */
public class SocketConnectorFactory {

    /**
     * Creates an ordinary, non-secured Jetty server jetty.
     *
     * @param server  Jetty server
     * @param host    host
     * @param port    port
     * @param usHTTP2 if true use HTTP2 connection, else HTTP1.x
     * @return - a server jetty
     */
    public static ServerConnector createSocketConnector(Server server, String host, int port, boolean usHTTP2, boolean trustForwardHeaders) {
        Assert.notNull(server, "'server' must not be null");
        Assert.notNull(host, "'host' must not be null");

        final AbstractConnectionFactory connectionFactory = usHTTP2 ?
            createHttp2ConnectionFactory(trustForwardHeaders) : createHttpConnectionFactory(trustForwardHeaders);
        ServerConnector connector = new ServerConnector(server, connectionFactory);
        initializeConnector(connector, host, port);
        return connector;
    }

    // jetty 12 verifies if a resource is readable, that breaks existing ( bad ) behaviour of tests
    public static boolean ENABLE_JETTY_11_COMPATIBILITY = false;

    // a hacky way to insert non existing resource
    static void forceInsertNonExistingResource(SslContextFactory.Server sslContextFactory, String someFieldName, String somePath) {
        try {
            Resource res = ResourceFactory.of(sslContextFactory).newResource(somePath);
            Field myField = SslContextFactory.class.getDeclaredField(someFieldName);
            myField.setAccessible(true);
            myField.set(sslContextFactory, res);
        } catch (Throwable ignore) {
            // ignore
        }
    }

    // run if fails runs the default
    static void runOrDefault(Runnable call, Runnable def) {
        try {
            call.run();
        } catch (RuntimeException rex) {
            if (ENABLE_JETTY_11_COMPATIBILITY) {
                def.run();
            }
        }
    }

    /**
     * Creates a ssl jetty socket jetty. Keystore required, truststore
     * optional. If truststore not specified keystore will be reused.
     *
     * @param server    Jetty server
     * @param sslStores the security sslStores.
     * @param host      host
     * @param port      port
     * @param useHTTP2  if true return HTTP2 enabled connector, else return HTTP1.x connector
     * @return a ssl socket jetty
     */
    public static ServerConnector createSecureSocketConnector(Server server,
                                                              String host,
                                                              int port,
                                                              SslStores sslStores,
                                                              boolean useHTTP2,
                                                              boolean trustForwardHeaders) {
        Assert.notNull(server, "'server' must not be null");
        Assert.notNull(host, "'host' must not be null");
        Assert.notNull(sslStores, "'sslStores' must not be null");

        final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        runOrDefault(() -> sslContextFactory.setKeyStorePath(sslStores.keystoreFile()), () -> {
            forceInsertNonExistingResource(sslContextFactory, "_keyStoreResource", sslStores.keystoreFile());
        });

        if (sslStores.keystorePassword() != null) {
            sslContextFactory.setKeyStorePassword(sslStores.keystorePassword());
        }

        if (sslStores.certAlias() != null) {
            sslContextFactory.setCertAlias(sslStores.certAlias());
        }

        if (sslStores.trustStoreFile() != null) {
            runOrDefault(() -> sslContextFactory.setTrustStorePath(sslStores.trustStoreFile()), () -> {
                forceInsertNonExistingResource(sslContextFactory, "_trustStoreResource", sslStores.trustStoreFile());
            });
        }

        if (sslStores.trustStorePassword() != null) {
            sslContextFactory.setTrustStorePassword(sslStores.trustStorePassword());
        }

        if (sslStores.needsClientCert()) {
            sslContextFactory.setNeedClientAuth(true);
            sslContextFactory.setWantClientAuth(true);
        }

        HttpConnectionFactory httpConnectionFactory = createHttpConnectionFactory(trustForwardHeaders);

        ServerConnector connector = new ServerConnector(server, sslContextFactory, httpConnectionFactory);
        initializeConnector(connector, host, port);
        return connector;
    }

    private static void initializeConnector(ServerConnector connector, String host, int port) {
        // Set some timeout options to make debugging easier.
        connector.setIdleTimeout(TimeUnit.HOURS.toMillis(1));
        connector.setHost(host);
        connector.setPort(port);
    }

    private static HttpConnectionFactory createHttpConnectionFactory(boolean trustForwardHeaders) {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        if (trustForwardHeaders) {
            httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        }
        return new HttpConnectionFactory(httpConfig);
    }

    private static HTTP2ServerConnectionFactory createHttp2ConnectionFactory(boolean trustForwardHeaders) {
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        if (trustForwardHeaders) {
            httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        }
        return new HTTP2ServerConnectionFactory(httpConfig);
    }
}
