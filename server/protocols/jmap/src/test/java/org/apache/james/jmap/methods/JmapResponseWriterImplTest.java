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

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableSet;

public class JmapResponseWriterImplTest {

    @Ignore
    @Test(expected=IllegalStateException.class)
    public void formatMethodResponseShouldWorkWhenNullJmapResponse() {
        String expectedMethod = "nwonMethod";
        String expectedClientId = "#1";
        String expectedId = "myId";

        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", expectedId);
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode(expectedClientId)} ;

        JmapResponseWriterImpl jmapResponseWriterImpl = new JmapResponseWriterImpl(ImmutableSet.of(new Jdk8Module()));
        ProtocolRequest request = ProtocolRequest.deserialize(nodes);
        ProtocolResponse response = jmapResponseWriterImpl.formatMethodResponse(JmapResponse
                .builder()
                .clientId(request.getClientId())
                .response(null)
                .build());

        assertThat(response.getResponseName()).isEqualTo(expectedMethod);
        assertThat(response.getResults().findValue("id").asText()).isEqualTo(expectedId);
        assertThat(response.getClientId()).isEqualTo(expectedClientId);
    }

    @Test
    public void formatMethodResponseShouldWork() {
        String expectedClientId = "#1";
        String expectedId = "myId";

        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", expectedId);
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode(expectedClientId)} ;

        ResponseClass responseClass = new ResponseClass();
        responseClass.id = expectedId;

        JmapResponseWriterImpl jmapResponseWriterImpl = new JmapResponseWriterImpl(ImmutableSet.of(new Jdk8Module()));
        ProtocolResponse response = jmapResponseWriterImpl.formatMethodResponse(
                JmapResponse
                .builder()
                .responseName(Method.Response.name("unknownMethod"))
                .clientId(ProtocolRequest.deserialize(nodes).getClientId())
                .response(responseClass)
                .build());

        assertThat(response.getResponseName()).isEqualTo(Method.Response.name("unknownMethod"));
        assertThat(response.getResults().findValue("id").asText()).isEqualTo(expectedId);
        assertThat(response.getClientId()).isEqualTo(ClientId.of(expectedClientId));
    }

    private static class ResponseClass {

        @SuppressWarnings("unused")
        public String id;
        
    }

    @Test
    public void formatErrorResponseShouldWork() {
        String expectedClientId = "#1";

        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "myId");
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode(expectedClientId)} ;

        JmapResponseWriterImpl jmapResponseWriterImpl = new JmapResponseWriterImpl(ImmutableSet.of(new Jdk8Module()));
        ProtocolResponse response = jmapResponseWriterImpl.formatMethodResponse(
                JmapResponse
                    .builder()
                    .clientId(ProtocolRequest.deserialize(nodes).getClientId())
                    .error()
                    .build());

        assertThat(response.getResponseName()).isEqualToComparingFieldByField(JmapResponse.ERROR_METHOD);
        assertThat(response.getResults().findValue("type").asText()).isEqualTo(JmapResponse.DEFAULT_ERROR_MESSAGE);
        assertThat(response.getClientId()).isEqualTo(ClientId.of(expectedClientId));
    }
}
