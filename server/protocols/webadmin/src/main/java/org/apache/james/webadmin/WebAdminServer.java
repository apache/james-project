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

package org.apache.james.webadmin;

import static spark.Spark.awaitInitialization;
import static spark.Spark.port;
import static spark.Spark.stop;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

public class WebAdminServer implements Configurable {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebAdminServer.class);
    public static final HierarchicalConfiguration NO_CONFIGURATION = null;
    public static final String WEBADMIN_PORT = "webadmin_port";

    private final int port;
    private final Set<Routes> routesList;

    // Spark do not allow to retrieve allocated port when using a random port. Thus we generate the port.
    public static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Inject
    @VisibleForTesting
    public WebAdminServer(@Named(WEBADMIN_PORT)int port, Set<Routes> routesList) {
        this.port = port;
        this.routesList = routesList;
    }

    public WebAdminServer(Routes... routes) throws IOException {
        this(findFreePort(), ImmutableSet.copyOf(routes));
    }

    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        port(port);
        routesList.forEach(Routes::define);
        LOGGER.info("Web admin server started");
    }

    @PreDestroy
    public void destroy() {
        stop();
        LOGGER.info("Web admin server stopped");
    }

    public void await() {
        awaitInitialization();
    }

    public int getPort() {
        return port;
    }
}
