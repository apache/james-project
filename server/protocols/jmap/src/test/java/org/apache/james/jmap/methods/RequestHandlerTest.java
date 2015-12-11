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

import java.io.IOException;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.james.jmap.model.AuthenticatedProtocolRequest;
import org.apache.james.jmap.model.ProtocolRequest;
import org.apache.james.jmap.model.ProtocolResponse;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.annotations.VisibleForTesting;
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

    public static class TestJmapResponse {

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

        private final JmapRequestParser jmapRequestParser;
        private final JmapResponseWriter jmapResponseWriter;

        @Inject
        @VisibleForTesting TestMethod(JmapRequestParser jmapRequestParser, JmapResponseWriter jmapResponseWriter) {
            this.jmapRequestParser = jmapRequestParser;
            this.jmapResponseWriter = jmapResponseWriter;
        }

        @Override
        public String methodName() {
            return "testMethod";
        }

        @Override
        public ProtocolResponse process(AuthenticatedProtocolRequest request) {
            try {
                TestJmapRequest typedRequest = jmapRequestParser.extractJmapRequest(request, TestJmapRequest.class);
                return jmapResponseWriter.formatMethodResponse(
                            JmapResponse
                                .forRequest(request)
                                .response(new TestJmapResponse(typedRequest.getId(), typedRequest.getName(), "works"))
                                .build());
            } catch (IOException e) {
                return jmapResponseWriter.formatErrorResponse(request);
            }
        }
    }

    private RequestHandler testee;
    private JmapRequestParser jmapRequestParser;
    private JmapResponseWriter jmapResponseWriter;
    private HttpServletRequest fakeHttpServletRequest;

    @Before
    public void setup() {
        jmapRequestParser = new JmapRequestParserImpl(ImmutableSet.of(new Jdk8Module()));
        jmapResponseWriter = new JmapResponseWriterImpl(ImmutableSet.of(new Jdk8Module()));
        fakeHttpServletRequest = null;
        testee = new RequestHandler(ImmutableSet.of(new TestMethod(jmapRequestParser, jmapResponseWriter)));
    }


    @Test(expected=IllegalStateException.class)
    public void processShouldThrowWhenUnknownMethod() {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{\"id\": \"id\"}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")} ;

        RequestHandler requestHandler = new RequestHandler(ImmutableSet.of());
        requestHandler.handle(AuthenticatedProtocolRequest.decorate(ProtocolRequest.deserialize(nodes), fakeHttpServletRequest));
    }

    @Test(expected=IllegalStateException.class)
    public void requestHandlerShouldThrowWhenAMethodIsRecordedTwice() {
        new RequestHandler(ImmutableSet.of(new TestMethod(jmapRequestParser, jmapResponseWriter), new TestMethod(jmapRequestParser, jmapResponseWriter)));
    }

    @Test(expected=IllegalStateException.class)
    public void requestHandlerShouldThrowWhenTwoMethodsWithSameName() {
        new RequestHandler(ImmutableSet.of(new NamedMethod("name"), new NamedMethod("name")));
    }

    @Test
    public void requestHandlerMayBeCreatedWhenTwoMethodsWithDifferentName() {
        new RequestHandler(ImmutableSet.of(new NamedMethod("name"), new NamedMethod("name2")));
    }

    private class NamedMethod implements Method {

        private final String methodName;

        public NamedMethod(String methodName) {
            this.methodName = methodName;
            
        }

        @Override
        public String methodName() {
            return methodName;
        }

        @Override
        public ProtocolResponse process(AuthenticatedProtocolRequest request) {
            return null;
        }
    }

    @Test
    public void processShouldWorkWhenKnownMethod() {
        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "testId");
        parameters.put("name", "testName");
        
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("testMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")} ;

        ProtocolResponse response = testee.handle(AuthenticatedProtocolRequest.decorate(ProtocolRequest.deserialize(nodes), fakeHttpServletRequest));

        assertThat(response.getResults().findValue("id").asText()).isEqualTo("testId");
        assertThat(response.getResults().findValue("name").asText()).isEqualTo("testName");
        assertThat(response.getResults().findValue("message").asText()).isEqualTo("works");
    }
}
