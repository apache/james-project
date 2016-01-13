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

package org.apache.james.jmap.methods;

import org.apache.james.jmap.model.ClientId;

import com.google.common.annotations.VisibleForTesting;

public class JmapResponse {

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        
        private Method.Response.Name responseName;
        private ClientId id;
        private Method.Response response;

        private Builder() {
        }

        public Builder responseName(Method.Response.Name name) {
            this.responseName = name;
            return this;
        }
        
        public Builder clientId(ClientId id) {
            this.id = id;
            return this;
        }
        
        public Builder response(Method.Response response) {
            this.response = response;
            return this;
        }

        public Builder error() {
            return error(DEFAULT_ERROR_MESSAGE);
        }

        public Builder error(String message) {
            this.response = new ErrorResponse(message);
            this.responseName = ERROR_METHOD;
            return this;
        }

        
        public JmapResponse build() {
            return new JmapResponse(responseName, id, response);
        }
    }

    public static class ErrorResponse implements Method.Response {
        
        private final String type;

        public ErrorResponse(String type) {
            this.type = type;
        }
        
        public String getType() {
            return type;
        }
    }
    
    @VisibleForTesting static final String DEFAULT_ERROR_MESSAGE = "Error while processing";
    public static final Method.Response.Name ERROR_METHOD = Method.Response.name("error");

    
    private final Method.Response.Name method;
    private final ClientId clientId;
    private final Method.Response response;
    
    private JmapResponse(Method.Response.Name method, ClientId clientId, Method.Response response) {
        this.method = method;
        this.clientId = clientId;
        this.response = response;
    }

    public Method.Response.Name getResponseName() {
        return method;
    }
    
    public Object getResponse() {
        return response;
    }
    
    public ClientId getClientId() {
        return clientId;
    }
}
