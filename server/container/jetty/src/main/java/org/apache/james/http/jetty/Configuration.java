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

import java.util.Objects;
import java.util.Optional;

import javax.servlet.Filter;
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

        private ImmutableMap.Builder<String, Object> mappings;
        private ImmutableMap.Builder<String, Object> filters;
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

            public Configuration.Builder with(Class<? extends Servlet> servletClass) {
                Preconditions.checkNotNull(servletClass);
                mappings.put(mappingUrl, servletClass);
                return Builder.this;
            }
        }
        
        public class FilterBinder {
            private String filterUrl;

            private FilterBinder(String filterUrl) {
                this.filterUrl = filterUrl;
            }
            
            public Configuration.Builder with(Filter filter) {
                Preconditions.checkNotNull(filter);
                filters.put(filterUrl, filter);
                return Builder.this;
            }

            public Configuration.Builder with(Class<? extends Filter> filterClass) {
                Preconditions.checkNotNull(filterClass);
                filters.put(filterUrl, filterClass);
                return Builder.this;
            }
        }
        
        private Builder() {
            mappings = ImmutableMap.builder();
            filters = ImmutableMap.builder();
            port = Optional.empty();
        }
        
        public ServletBinder serve(String mappingUrl) {
            Preconditions.checkNotNull(mappingUrl);
            Preconditions.checkArgument(!mappingUrl.trim().isEmpty());
            return new ServletBinder(mappingUrl);
        }
        
        public FilterBinder filter(String mappingUrl) {
            Preconditions.checkNotNull(mappingUrl);
            Preconditions.checkArgument(!mappingUrl.trim().isEmpty());
            return new FilterBinder(mappingUrl);
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
            return new Configuration(mappings.build(), filters.build(), port);
        }
    }

    private final ImmutableMap<String, Object> mappings;
    private final ImmutableMap<String, Object> filters;
    private final Optional<Integer> port;

    private Configuration(ImmutableMap<String, Object> mappings, ImmutableMap<String, Object> filters, Optional<Integer> port) {
        this.mappings = mappings;
        this.filters = filters;
        this.port = port;
    }
    
    public ImmutableMap<String, Object> getMappings() {
        return mappings;
    }

    public ImmutableMap<String, Object> getFilters() {
        return filters;
    }

    
    public Optional<Integer> getPort() {
        return port;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(mappings, port);
    }
    
    @Override
    public boolean equals(Object that) {
        if (that instanceof Configuration) {
            Configuration other = (Configuration) that;
            return Objects.equals(mappings, other.mappings)
                    && Objects.equals(filters, other.filters)
                    && Objects.equals(port, other.port);
        }
        return false;
    }
    
    @Override
    public String toString() {
        return com.google.common.base.Objects.toStringHelper(getClass())
                .add("mappings", mappings)
                .add("filters", filters)
                .add("port", port)
                .toString();
    }

}