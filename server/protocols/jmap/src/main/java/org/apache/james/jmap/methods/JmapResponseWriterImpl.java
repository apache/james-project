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

import java.util.Set;

import javax.inject.Inject;

import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;

public class JmapResponseWriterImpl implements JmapResponseWriter {

    @VisibleForTesting static final String DEFAULT_ERROR_MESSAGE = "Error while processing";
    @VisibleForTesting static final String ERROR_METHOD = "error";

    private final ObjectMapper objectMapper;

    @Inject
    public JmapResponseWriterImpl(Set<Module> jacksonModules) {
        this.objectMapper = new ObjectMapper().registerModules(jacksonModules);
    }

    @Override
    public ProtocolResponse formatMethodResponse(ProtocolRequest request, JmapResponse jmapResponse) {
        ObjectNode objectNode = objectMapper.valueToTree(jmapResponse);
        return new ProtocolResponse(request.getMethod(), objectNode, request.getClientId());
    }

    @Override
    public ProtocolResponse formatErrorResponse(ProtocolRequest request) {
        return formatErrorResponse(request, DEFAULT_ERROR_MESSAGE);
    }

    @Override
    public ProtocolResponse formatErrorResponse(ProtocolRequest request, String error) {
        ObjectNode errorObjectNode = new ObjectNode(new JsonNodeFactory(false)).put("type", error);
        return new ProtocolResponse(ERROR_METHOD, errorObjectNode, request.getClientId());
    }
}
