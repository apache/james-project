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

package org.apache.james.jmap;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.lifecycle.api.Configurable;

import com.google.common.base.Throwables;


@Singleton
public class JMAPServer implements Configurable {

    public static final String DEFAULT_JMAP_PORT = "defaultJMAPPort";
    
    private final JettyHttpServer server;

    @Inject
    private JMAPServer(@Named(DEFAULT_JMAP_PORT) int port, 
            AuthenticationServlet authenticationServlet, JMAPServlet jmapServlet,
            AuthenticationFilter authenticationFilter) {

        server = JettyHttpServer.create(Configuration.builder()
                .port(port)
                .serve("/authentication").with(authenticationServlet)
                .filter("/authentication").with(new BypassOnPostFilter(authenticationFilter))
                .serve("/jmap").with(jmapServlet)
                .filter("/jmap").with(authenticationFilter)
                .build());
    }
    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        try {
            server.start();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    @PreDestroy
    public void stop() {
        try {
            server.stop();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    public int getPort() {
        return server.getPort();
    }

}
