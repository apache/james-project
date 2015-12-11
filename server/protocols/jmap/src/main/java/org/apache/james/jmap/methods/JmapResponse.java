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
import org.apache.james.jmap.model.ProtocolRequest;

import com.google.common.annotations.VisibleForTesting;

public class JmapResponse {

    public static Builder builder() {
        return new Builder();
    }
    
    public static Builder forRequest(ProtocolRequest request) {
        return builder().clientId(request.getClientId()).method(request.getMethod());
    }
    
    public static class Builder {
        
        private Method.Name method;
        private ClientId id;
        private Object response;

        private Builder() {
        }

        public Builder method(Method.Name name) {
            this.method = name;
            return this;
        }
        
        public Builder clientId(ClientId id) {
            this.id = id;
            return this;
        }
        
        public Builder response(Object response) {
            this.response = response;
            return this;
        }

        public Builder error() {
            return error(DEFAULT_ERROR_MESSAGE);
        }

        public Builder error(String message) {
            this.response = new ErrorResponse(message);
            this.method = ERROR_METHOD;
            return this;
        }

        
        public JmapResponse build() {
            return new JmapResponse(method, id, response);
        }
    }

    public static class ErrorResponse {
        
        private final String type;

        public ErrorResponse(String type) {
            this.type = type;
        }
        
        public String getType() {
            return type;
        }
    }
    
    @VisibleForTesting static final String DEFAULT_ERROR_MESSAGE = "Error while processing";
    @VisibleForTesting static final Method.Name ERROR_METHOD = Method.name("error");

    private final Method.Name method;
    private final ClientId clientId;
    private final Object response;
    
    private JmapResponse(Method.Name method, ClientId clientId, Object response) {
        this.method = method;
        this.clientId = clientId;
        this.response = response;
    }

    public Method.Name getMethod() {
        return method;
    }
    
    public Object getResponse() {
        return response;
    }
    
    public ClientId getClientId() {
        return clientId;
    }
}
