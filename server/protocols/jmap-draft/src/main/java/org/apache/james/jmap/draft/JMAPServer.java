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

package org.apache.james.jmap.draft;

import static org.apache.james.jmap.draft.BypassAuthOnRequestMethod.bypass;
import static org.zalando.logbook.HeaderFilters.authorization;

import java.util.Optional;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.apache.james.http.jetty.Configuration;
import org.apache.james.http.jetty.Configuration.Builder;
import org.apache.james.http.jetty.JettyHttpServer;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.Port;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.DefaultHttpLogWriter;
import org.zalando.logbook.DefaultHttpLogWriter.Level;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.servlet.LogbookFilter;

import com.github.fge.lambdas.Throwing;

public class JMAPServer implements Startable {

    private static final Logger HTTP_JMAP_LOGGER = LoggerFactory.getLogger("http.jmap");
    private final Optional<JettyHttpServer> server;

    @Inject
    private JMAPServer(JMAPConfiguration jmapConfiguration,
                       AuthenticationServlet authenticationServlet, JMAPServlet jmapServlet, DownloadServlet downloadServlet, UploadServlet uploadServlet,
                       AuthenticationFilter authenticationFilter, UserProvisioningFilter userProvisioningFilter, DefaultMailboxesProvisioningFilter defaultMailboxesProvisioningFilter) {
        if (jmapConfiguration.isEnabled()) {
            server = Optional.of(JettyHttpServer.create(
                configurationBuilderFor(jmapConfiguration)
                    .serve(JMAPUrls.AUTHENTICATION)
                        .with(authenticationServlet)
                    .filter(JMAPUrls.AUTHENTICATION)
                        .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("POST").and("OPTIONS").only()))
                        .and(new MDCFilter())
                        .only()
                    .serve(JMAPUrls.JMAP)
                        .with(jmapServlet)
                    .filter(JMAPUrls.JMAP)
                        .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                        .and(new LogbookFilter(logbook()))
                        .and(userProvisioningFilter)
                        .and(defaultMailboxesProvisioningFilter)
                        .and(new MDCFilter())
                        .only()
                    .serveAsOneLevelTemplate(JMAPUrls.DOWNLOAD)
                        .with(downloadServlet)
                    .filterAsOneLevelTemplate(JMAPUrls.DOWNLOAD)
                        .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                        .and(new MDCFilter())
                        .only()
                    .serve(JMAPUrls.UPLOAD)
                        .with(uploadServlet)
                    .filterAsOneLevelTemplate(JMAPUrls.UPLOAD)
                        .with(new AllowAllCrossOriginRequests(bypass(authenticationFilter).on("OPTIONS").only()))
                        .and(new MDCFilter())
                        .only()
                    .build()));
        } else {
            server = Optional.empty();
        }
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

    private Logbook logbook() {
        return Logbook.builder()
                .headerFilter(authorization())
                .writer(new DefaultHttpLogWriter(HTTP_JMAP_LOGGER, Level.DEBUG))
                .build();
    }

    public void start() {
        server.ifPresent(Throwing.consumer(JettyHttpServer::start).sneakyThrow());
    }

    @PreDestroy
    public void stop() {
        server.ifPresent(Throwing.consumer(JettyHttpServer::stop).sneakyThrow());
    }

    public Port getPort() {
        return Port.of(server.map(JettyHttpServer::getPort).orElseThrow(() -> new RuntimeException("JMAP server was disabled. No port bound")));
    }
}
