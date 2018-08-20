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
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.Property;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class JmapResponseWriterImplTest {
    private JmapResponseWriterImpl testee;

    @Before
    public void setup() {
        testee = new JmapResponseWriterImpl(new ObjectMapperFactory(new InMemoryId.Factory(), new InMemoryMessageId.Factory()));
    }

    @Ignore
    @Test(expected = IllegalStateException.class)
    public void formatMethodResponseShouldWorkWhenNullJmapResponse() {
        String expectedMethod = "nwonMethod";
        String expectedClientId = "#1";
        String expectedId = "myId";

        Stream<ProtocolResponse> response = testee.formatMethodResponse(Stream.of(JmapResponse
                .builder()
                .clientId(ClientId.of(expectedClientId))
                .response(null)
                .build()));

        List<ProtocolResponse> responseList = response.collect(Collectors.toList());
        assertThat(responseList).hasSize(1)
                .extracting(ProtocolResponse::getResponseName, x -> x.getResults().get("id").asText(), ProtocolResponse::getClientId)
                .containsExactly(tuple(expectedMethod, expectedId, expectedClientId));
    }

    @Test
    public void formatMethodResponseShouldWork() {
        String expectedClientId = "#1";
        String expectedId = "myId";

        ResponseClass responseClass = new ResponseClass();
        responseClass.id = expectedId;

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                .builder()
                .responseName(Method.Response.name("unknownMethod"))
                .clientId(ClientId.of(expectedClientId))
                .response(responseClass)
                .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(1)
                .extracting(ProtocolResponse::getResponseName, x -> x.getResults().get("id").asText(), ProtocolResponse::getClientId)
                .containsExactly(tuple(Method.Response.name("unknownMethod"), expectedId, ClientId.of(expectedClientId)));
    }

    private static class ResponseClass implements Method.Response {

        @SuppressWarnings("unused")
        public String id;
        
    }

    @Test
    public void formatMethodResponseShouldFilterFieldsWhenProperties() {
        ObjectResponseClass responseClass = new ObjectResponseClass();
        responseClass.list = ImmutableList.of(new ObjectResponseClass.Foo("id", "name"));
        Property property = () -> "id";

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                .builder()
                .responseName(Method.Response.name("unknownMethod"))
                .clientId(ClientId.of("#1"))
                .properties(ImmutableSet.of(property))
                .response(responseClass)
                .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(1);
        JsonNode firstObject = Iterables.getOnlyElement(response).getResults().get("list").elements().next();
        assertThat(firstObject.get("id").asText()).isEqualTo("id");
        assertThat(firstObject.get("name")).isNull();
    }



    @Test
    public void formatMethodResponseShouldNotFilterFieldsWhenSecondCallWithoutProperties() {
        ObjectResponseClass responseClass = new ObjectResponseClass();
        responseClass.list = ImmutableList.of(new ObjectResponseClass.Foo("id", "name"));
        Property property = () -> "id";

        @SuppressWarnings("unused")
        Stream<ProtocolResponse> ignoredResponse = testee.formatMethodResponse(
                Stream.of(JmapResponse
                        .builder()
                        .responseName(Method.Response.name("unknownMethod"))
                        .clientId(ClientId.of("#1"))
                        .properties(ImmutableSet.of(property))
                        .response(responseClass)
                        .build()));

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                .builder()
                .responseName(Method.Response.name("unknownMethod"))
                .clientId(ClientId.of("#1"))
                .response(responseClass)
                .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(1);
        JsonNode firstObject = Iterables.getOnlyElement(response).getResults().get("list").elements().next();
        assertThat(firstObject.get("id").asText()).isEqualTo("id");
        assertThat(firstObject.get("name").asText()).isEqualTo("name");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void formatMethodResponseShouldFilterRightFieldsForEachResponse() {
        ObjectResponseClass responseClass = new ObjectResponseClass();
        responseClass.list = ImmutableList.of(new ObjectResponseClass.Foo("id", "name"));
        Property idProperty = () -> "id";
        Property nameProperty = () -> "name";

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                            .builder()
                            .responseName(Method.Response.name("unknownMethod"))
                            .clientId(ClientId.of("#1"))
                            .properties(ImmutableSet.of(idProperty, nameProperty))
                            .response(responseClass)
                            .build(),
                        JmapResponse
                            .builder()
                            .responseName(Method.Response.name("unknownMethod"))
                            .clientId(ClientId.of("#1"))
                            .properties(ImmutableSet.of(idProperty))
                            .response(responseClass)
                            .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(2)
                .extracting(x -> x.getResults().get("list").elements().next())
                .extracting(
                        x -> x.get("id").asText(),
                        x -> Optional.ofNullable(x.get("name")).map(JsonNode::asText).orElse(null))
                .containsExactly(tuple("id", "name"), tuple("id", null));
    }

    @SuppressWarnings("unused")
    private static class ObjectResponseClass implements Method.Response {
        @JsonFilter("propertiesFilter")
        private static class Foo {
            public String id;
            public String name;
            
            public Foo(String id, String name) {
                this.id = id;
                this.name = name;
            }
        }
        
        public List<Foo> list;
    }

    @Test
    public void formatErrorResponseShouldWork() {
        String expectedClientId = "#1";

        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "myId");
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode(expectedClientId)};

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                    .builder()
                    .clientId(ProtocolRequest.deserialize(nodes).getClientId())
                    .error()
                    .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(1)
                .extracting(ProtocolResponse::getResponseName, x -> x.getResults().get("type").asText(), ProtocolResponse::getClientId)
                .containsExactly(tuple(ErrorResponse.ERROR_METHOD, ErrorResponse.DEFAULT_ERROR_MESSAGE, ClientId.of(expectedClientId)));
    }

    @Test
    public void formatErrorResponseShouldWorkWithTypeAndDescription() {
        String expectedClientId = "#1";

        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "myId");
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode(expectedClientId)};

        List<ProtocolResponse> response = testee.formatMethodResponse(
                Stream.of(JmapResponse
                    .builder()
                    .clientId(ProtocolRequest.deserialize(nodes).getClientId())
                    .error(ErrorResponse
                            .builder()
                            .type("errorType")
                            .description("complete description")
                            .build())
                    .build()))
                .collect(Collectors.toList());

        assertThat(response).hasSize(1)
                .extracting(ProtocolResponse::getResponseName, x -> x.getResults().get("type").asText(), x -> x.getResults().get("description").asText(), ProtocolResponse::getClientId)
                .containsExactly(tuple(ErrorResponse.ERROR_METHOD, "errorType", "complete description", ClientId.of(expectedClientId)));
    }

}
