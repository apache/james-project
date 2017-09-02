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

package org.apache.james.http.jetty;

import java.io.Closeable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.BiConsumer;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimaps;

public class JettyHttpServer implements Closeable {
    
    private static final int A_SINGLE_THREAD = 1;
    private static final int MAX_THREAD = 200;

    public static JettyHttpServer create(Configuration configuration) {
        return new JettyHttpServer(configuration);
    }

    private final Server server;
    private ServerConnector serverConnector;
    private final Configuration configuration;

    private JettyHttpServer(Configuration configuration) {
        this.configuration = configuration;
        this.server = new Server(new QueuedThreadPool(MAX_THREAD, A_SINGLE_THREAD));
        this.server.addConnector(buildServerConnector(configuration));
        this.server.setHandler(buildServletHandler(configuration));
    }

    private ServerConnector buildServerConnector(Configuration configuration) {
        this.serverConnector = new ServerConnector(server);
        configuration.getPort().ifPresent(serverConnector::setPort);
        return serverConnector;
    }

    private ServletHandler buildServletHandler(Configuration configuration) {
        ServletHandler servletHandler = new ServletHandler();
        
        BiConsumer<String, ServletHolder> addServletMapping = (path, servletHolder) -> servletHandler.addServletWithMapping(servletHolder, path);
        BiConsumer<String, Collection<FilterHolder>> addFilterMappings = 
                (path, filterHolders) -> filterHolders.forEach(
                        filterHolder -> servletHandler.addFilterWithMapping(filterHolder, path, EnumSet.of(DispatcherType.REQUEST)));
                
        Maps.transformEntries(configuration.getMappings(), this::toServletHolder).forEach(addServletMapping);
        Multimaps.transformEntries(configuration.getFilters(), this::toFilterHolder).asMap().forEach(addFilterMappings);
        return servletHandler;
    }

    
    @SuppressWarnings("unchecked")
    private ServletHolder toServletHolder(String path, Object value) {
        if (value instanceof Servlet) {
            return new ServletHolder((Servlet) value);
        }
        return new ServletHolder((Class<? extends Servlet>)value);
    }
    
    @SuppressWarnings("unchecked")
    private FilterHolder toFilterHolder(String path, Object value) {
        if (value instanceof Filter) {
            return new FilterHolder((Filter)value);
        }
        return new FilterHolder((Class<? extends Filter>)value);
    }
    
    public JettyHttpServer start() throws Exception {
        server.start();
        return this;
    }
    
    public void stop() throws Exception {
        server.stop();
    }

    public int getPort() {
        return serverConnector.getLocalPort();
    }

    public Configuration getConfiguration() {
        return configuration;
    }
    
    @Override
    public void close() {
        try {
            stop();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
    
}
