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

import static org.apache.james.jmap.BypassAuthOnRequestMethod.bypass;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.Configuration.Builder;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.lifecycle.api.Configurable;

import com.google.common.base.Throwables;

public class JMAPServer implements Configurable {

    private final JettyHttpServer server;

    @Inject
    private JMAPServer(JMAPConfiguration jmapConfiguration,
                       AuthenticationServlet authenticationServlet, JMAPServlet jmapServlet, DownloadServlet downloadServlet, UploadServlet uploadServlet,
                       AuthenticationFilter authenticationFilter, FirstUserConnectionFilter firstUserConnectionFilter) {

        server = JettyHttpServer.create(
                configurationBuilderFor(jmapConfiguration)
                        .serve(JMAPUrls.AUTHENTICATION)
                            .with(authenticationServlet)
                        .filter(JMAPUrls.AUTHENTICATION)
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("POST").and("OPTIONS").only()))
                            .only()
                        .serve(JMAPUrls.JMAP)
                            .with(jmapServlet)
                        .filter(JMAPUrls.JMAP)
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                            .and(firstUserConnectionFilter)
                            .only()
                        .serveAsOneLevelTemplate(JMAPUrls.DOWNLOAD)
                            .with(downloadServlet)
                        .filterAsOneLevelTemplate(JMAPUrls.DOWNLOAD)
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                            .only()
                        .serve(JMAPUrls.UPLOAD)
                            .with(uploadServlet)
                        .filterAsOneLevelTemplate(JMAPUrls.UPLOAD)
                            .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                            .only()
                        .build());
    }

    private Builder configurationBuilderFor(JMAPConfiguration jmapConfiguration) {
        Builder builder = Configuration.builder();
        if (jmapConfiguration.getPort().isPresent()) {
            builder.port(jmapConfiguration.getPort().get());
        } else {
            builder.randomPort();
        }
        return builder;
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
