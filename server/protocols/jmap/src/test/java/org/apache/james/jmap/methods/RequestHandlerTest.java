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
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ClientId;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

public class RequestHandlerTest {

    public static class TestJmapRequest implements JmapRequest {

        public String id;
        public String name;

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class TestJmapResponse implements Method.Response {

        private final String id;
        private final String name;
        private final String message;

        public TestJmapResponse(String id, String name, String message) {
            this.id = id;
            this.name = name;
            this.message = message;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class TestMethod implements Method {

        @Inject
        @VisibleForTesting TestMethod() {
        }

        @Override
        public Method.Request.Name requestHandled() {
            return Method.Request.name("getTestMethod");
        }

        @Override
        public Class<? extends JmapRequest> requestType() {
            return TestJmapRequest.class;
        }

        @Override
        public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
            Preconditions.checkArgument(request instanceof TestJmapRequest);
            TestJmapRequest typedRequest = (TestJmapRequest) request;
            return Stream.of(
                    JmapResponse.builder()
                            .response(new TestJmapResponse(typedRequest.getId(), typedRequest.getName(), "works"))
                            .responseName(Response.name("test"))
                            .clientId(ClientId.of("#0"))
                            .build());
        }
    }

    private RequestHandler testee;
    private JmapRequestParser jmapRequestParser;
    private JmapResponseWriter jmapResponseWriter;
    private HttpServletRequest mockHttpServletRequest;

    @Before
    public void setup() {
        ObjectMapperFactory objectMapperFactory = new ObjectMapperFactory(new InMemoryId.Factory(), new InMemoryMessageId.Factory());
        jmapRequestParser = new JmapRequestParserImpl(objectMapperFactory);
        jmapResponseWriter = new JmapResponseWriterImpl(objectMapperFactory);
        mockHttpServletRequest = mock(HttpServletRequest.class);
        testee = new RequestHandler(ImmutableSet.of(new TestMethod()), jmapRequestParser, jmapResponseWriter);
    }


    @Test(expected = IllegalStateException.class)
    public void processShouldThrowWhenUnknownMethod() throws Exception {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{\"id\": \"id\"}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        RequestHandler requestHandler = new RequestHandler(ImmutableSet.of(), jmapRequestParser, jmapResponseWriter);
        requestHandler.handle(AuthenticatedProtocolRequest.decorate(ProtocolRequest.deserialize(nodes), mockHttpServletRequest));
    }

    @Test(expected = IllegalStateException.class)
    public void requestHandlerShouldThrowWhenAMethodIsRecordedTwice() {
        new RequestHandler(
                ImmutableSet.of(
                        new TestMethod(),
                        new TestMethod()),
                jmapRequestParser, 
                jmapResponseWriter);
    }

    @Test(expected = IllegalStateException.class)
    public void requestHandlerShouldThrowWhenTwoMethodsWithSameName() {
        new RequestHandler(
                ImmutableSet.of(
                        new NamedMethod(Method.Request.name("name")),
                        new NamedMethod(Method.Request.name("name"))),
                jmapRequestParser, 
                jmapResponseWriter);
    }

    @Test
    public void requestHandlerMayBeCreatedWhenTwoMethodsWithDifferentName() {
        new RequestHandler(
                ImmutableSet.of(
                        new NamedMethod(Method.Request.name("name")), 
                        new NamedMethod(Method.Request.name("name2"))),
                jmapRequestParser, 
                jmapResponseWriter);
    }

    private class NamedMethod implements Method {

        private final Method.Request.Name methodName;

        public NamedMethod(Method.Request.Name methodName) {
            this.methodName = methodName;
            
        }

        @Override
        public Method.Request.Name requestHandled() {
            return methodName;
        }
        
        @Override
        public Class<? extends JmapRequest> requestType() {
            return null;
        }
        
        @Override
        public Stream<JmapResponse> process(JmapRequest request, ClientId clientId, MailboxSession mailboxSession) {
            return null;
        }
    }

    @Test
    public void processShouldWorkWhenKnownMethod() throws Exception {
        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "testId");
        parameters.put("name", "testName");
        
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getTestMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        List<ProtocolResponse> responses = testee.handle(AuthenticatedProtocolRequest.decorate(ProtocolRequest.deserialize(nodes), mockHttpServletRequest))
                .collect(Collectors.toList());

        assertThat(responses).hasSize(1)
                .extracting(
                        x -> x.getResults().findValue("id").asText(),
                        x -> x.getResults().findValue("name").asText(),
                        x -> x.getResults().findValue("message").asText())
                .containsExactly(tuple("testId", "testName", "works"));
    }
}
