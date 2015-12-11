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

import java.util.Optional;

import javax.servlet.Servlet;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

public class Configuration {

    public static Configuration defaultConfiguration() {
        return builder().build();
    }

    public static Configuration.Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        
        private static final Range<Integer> VALID_PORT_RANGE = Range.closed(1, 65535);

        private ImmutableMap.Builder<String, Servlet> mappings;
        private Optional<Integer> port;
        
        public class ServletBinder {
            private String mappingUrl;

            private ServletBinder(String mappingUrl) {
                this.mappingUrl = mappingUrl;
            }
            
            public Configuration.Builder with(Servlet servlet) {
                Preconditions.checkNotNull(servlet);
                mappings.put(mappingUrl, servlet);
                return Builder.this;
            }
        }
        
        private Builder() {
            mappings = ImmutableMap.builder();
            port = Optional.empty();
        }
        
        public ServletBinder serve(String mappingUrl) {
            Preconditions.checkNotNull(mappingUrl);
            Preconditions.checkArgument(!mappingUrl.trim().isEmpty());
            return new ServletBinder(mappingUrl);
        }

        public Builder port(int port) {
            Preconditions.checkArgument(VALID_PORT_RANGE.contains(port));
            this.port = Optional.of(port);
            return this;
        }
        
        public Builder randomPort() {
            this.port = Optional.empty();
            return this;
        }
        
        public Configuration build() {
            return new Configuration(mappings.build(), port);
        }
    }

    private final ImmutableMap<String, Servlet> mappings;
    private final Optional<Integer> port;

    private Configuration(ImmutableMap<String, Servlet> mappings, Optional<Integer> port) {
        this.mappings = mappings;
        this.port = port;
    }
    
    public ImmutableMap<String, Servlet> getMappings() {
        return mappings;
    }
    
    public Optional<Integer> getPort() {
        return port;
    }
}