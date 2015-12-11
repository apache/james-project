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

import javax.servlet.Servlet;

import com.google.common.collect.ImmutableMap;

public class Configuration {

    public static Configuration empty() {
        return builder().build();
    }

    public static Configuration.Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        
        private ImmutableMap.Builder<String, Servlet> mappings;
        
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
        }
        
        public ServletBinder serve(String mappingUrl) {
            return new ServletBinder(mappingUrl);
        }

        public Configuration build() {
            return new Configuration(mappings.build());
        }
    }

    private final ImmutableMap<String, Servlet> mappings;

    public Configuration(ImmutableMap<String, Servlet> mappings) {
        this.mappings = mappings;
    }
    
    public ImmutableMap<String, Servlet> getMappings() {
        return mappings;
    }
}