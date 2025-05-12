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

import java.io.IOException;
import java.net.ServerSocket;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.DispatcherType;

import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.embeddedserver.EmbeddedServer;
import spark.embeddedserver.VirtualThreadAware;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.embeddedserver.jetty.websocket.WebSocketHandlerWrapper;
import spark.embeddedserver.jetty.websocket.WebSocketServletContextHandlerFactory;
import spark.http.matching.MatcherFilter;
import spark.ssl.SslStores;

/**
 * Fork from spark.embeddedserver.jetty.EmbeddedJettyServer
 */
public class EmbeddedJettyServer extends VirtualThreadAware.Proxy implements EmbeddedServer {

    private static final int SPARK_DEFAULT_PORT = 4567;
    private static final String NAME = "Spark";

    private final JettyServerFactory serverFactory;
    private final boolean httpOnly;
    private final MatcherFilter matcherFilter;
    private Server server;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<String, WebSocketHandlerWrapper> webSocketHandlers;
    private Optional<Long> webSocketIdleTimeoutMillis;

    private ThreadPool threadPool = null;
    private boolean trustForwardHeaders = true; // true by default

    public EmbeddedJettyServer(JettyServerFactory serverFactory, boolean httpOnly, MatcherFilter matcherFilter) {
        super(serverFactory);
        this.serverFactory = serverFactory;
        this.httpOnly = httpOnly;
        this.matcherFilter = matcherFilter;
    }

    @Override
    public void configureWebSockets(Map<String, WebSocketHandlerWrapper> webSocketHandlers,
                                    Optional<Long> webSocketIdleTimeoutMillis) {

        this.webSocketHandlers = webSocketHandlers;
        this.webSocketIdleTimeoutMillis = webSocketIdleTimeoutMillis;
    }

    @Override
    public void trustForwardHeaders(boolean trust) {
        this.trustForwardHeaders = trust;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int ignite(String host,
                      int port,
                      boolean useHTTP2,
                      SslStores sslStores,
                      int maxThreads,
                      int minThreads,
                      int threadIdleTimeoutMillis) throws Exception {

        boolean hasCustomizedConnectors = false;

        if (port == 0) {
            try (ServerSocket s = new ServerSocket(0)) {
                port = s.getLocalPort();
            } catch (IOException e) {
                logger.error("Could not get first available port (port set to 0), using default: {}", SPARK_DEFAULT_PORT);
                port = SPARK_DEFAULT_PORT;
            }
        }

        // Create instance of jetty server with either default or supplied queued thread pool
        if (threadPool == null) {
            server = serverFactory.create(maxThreads, minThreads, threadIdleTimeoutMillis);
        } else {
            server = serverFactory.create(threadPool);
        }

        ServerConnector connector;

        if (sslStores == null) {
            connector = SocketConnectorFactory.createSocketConnector(server, host, port, useHTTP2, trustForwardHeaders);
        } else {
            connector = SocketConnectorFactory.createSecureSocketConnector(server, host, port, sslStores, useHTTP2, trustForwardHeaders);
        }

        Connector[] previousConnectors = server.getConnectors();
        server = connector.getServer();
        if (previousConnectors.length != 0) {
            server.setConnectors(previousConnectors);
            hasCustomizedConnectors = true;
        } else {
            server.setConnectors(new Connector[]{connector});
        }

        final ServletContextHandler webSocketServletContextHandler =
            WebSocketServletContextHandlerFactory.create(webSocketHandlers, webSocketIdleTimeoutMillis);

        final ServletContextHandler servletContextHandler = webSocketServletContextHandler == null ?
            new ServletContextHandler() : webSocketServletContextHandler;

        final SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.getSessionCookieConfig().setHttpOnly(httpOnly);
        servletContextHandler.setSessionHandler(sessionHandler);

        servletContextHandler.addFilter(matcherFilter, "/*", EnumSet.allOf(DispatcherType.class));
        servletContextHandler.getServletHandler().setDecodeAmbiguousURIs(true);
        server.setHandler(servletContextHandler);

        logger.info("== {} has ignited ...", NAME);
        if (hasCustomizedConnectors) {
            logger.info(">> Listening on Custom Server ports!");
        } else {
            logger.info(">> Listening on {}:{}", host, port);
        }

        server.start();
        return port;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void join() throws InterruptedException {
        server.join();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void extinguish() {
        logger.info(">>> {} shutting down ...", NAME);
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            logger.error("stop failed", e);
            System.exit(100); // NOSONAR
        }
        logger.info("done");
    }

    @Override
    public int activeThreadCount() {
        if (server == null) {
            return 0;
        }
        return server.getThreadPool().getThreads() - server.getThreadPool().getIdleThreads();
    }

    /**
     * Sets optional thread pool for jetty server.  This is useful for overriding the default thread pool
     * behaviour for example io.dropwizard.metrics.jetty9.InstrumentedQueuedThreadPool.
     * @param threadPool thread pool
     * @return Builder pattern - returns this instance
     */
    public EmbeddedJettyServer withThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
        return this;
    }
}