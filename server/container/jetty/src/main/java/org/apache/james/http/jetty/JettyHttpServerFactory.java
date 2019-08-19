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

import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.james.http.jetty.Configuration.Builder;

public class JettyHttpServerFactory {

    public List<JettyHttpServer> createServers(HierarchicalConfiguration config) throws Exception {
        List<HierarchicalConfiguration> configs = config.configurationsAt("httpserver");
        return configs.stream()
                .map(this::buildConfiguration)
                .map(JettyHttpServer::create)
                .collect(Collectors.toList());
    }

    private Configuration buildConfiguration(HierarchicalConfiguration serverConfig) {
        Builder builder = Configuration.builder();
        
        boolean randomPort = serverConfig.getBoolean("port[@random]", false);
        Integer port = serverConfig.getInteger("port[@fixed]", null);
        if (randomPort && port != null) {
            throw new ConfigurationException("Random port is not compatible with fixed port");
        }
        if (randomPort) {
            builder.randomPort();
        }
        if (port != null) {
            builder.port(port);
        }
        List<HierarchicalConfiguration> mappings = serverConfig.configurationsAt("mappings.mapping");
        for (HierarchicalConfiguration mapping: mappings) {
            String classname = mapping.getString("servlet");
            Class<? extends Servlet> servletClass = findServlet(classname);
            builder.serve(mapping.getString("path")).with(servletClass);
        }
        List<HierarchicalConfiguration> filters = serverConfig.configurationsAt("filters.mapping");
        for (HierarchicalConfiguration mapping: filters) {
            String classname = mapping.getString("filter");
            Class<? extends Filter> filterClass = findFilter(classname);
            builder.filter(mapping.getString("path")).with(filterClass);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends Servlet> findServlet(String classname) {
        try {
            return (Class<? extends Servlet>) ClassLoader.getSystemClassLoader().loadClass(classname);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(String.format("'%s' servlet cannot be found", classname), e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Class<? extends Filter> findFilter(String classname) {
        try {
            return (Class<? extends Filter>) ClassLoader.getSystemClassLoader().loadClass(classname);
        } catch (ClassNotFoundException e) {
            throw new ConfigurationException(String.format("'%s' filter cannot be found", classname), e);
        }
    }
}
