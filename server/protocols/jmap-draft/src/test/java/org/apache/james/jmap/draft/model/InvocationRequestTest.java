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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.MethodCallId;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class InvocationRequestTest {

    @Test(expected = IllegalStateException.class)
    public void deserializedRequestsShouldThrowWhenNotEnoughElements() throws Exception {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getAccounts"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{}")};

        InvocationRequest.deserialize(nodes);
    }

    @Test(expected = IllegalStateException.class)
    public void deserializedRequestsShouldThrowWhenTooMuchElements() throws Exception {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getAccounts"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#0"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("tooMuch")};

        InvocationRequest.deserialize(nodes);
    }

    @Test(expected = IllegalStateException.class)
    public void deserializedRequestsShouldThrowWhenFirstParameterIsNotString() throws JsonParseException, JsonMappingException, IOException {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).booleanNode(true),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#0")};

        InvocationRequest.deserialize(nodes);
    }

    @Test(expected = IllegalStateException.class)
    public void deserializedRequestsShouldThrowWhenSecondParameterIsNotJson() throws JsonParseException, JsonMappingException, IOException {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getAccounts"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("true"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#0")};

        InvocationRequest.deserialize(nodes);
    }

    @Test(expected = IllegalStateException.class)
    public void deserializedRequestsShouldThrowWhenThirdParameterIsNotString() throws JsonParseException, JsonMappingException, IOException {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getAccounts"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{}"),
                new ObjectNode(new JsonNodeFactory(false)).booleanNode(true)};

        InvocationRequest.deserialize(nodes);
    }

    @Test
    public void deserializedRequestsShouldWorkWhenSingleRequest() throws JsonParseException, JsonMappingException, IOException {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("getAccounts"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{\"id\": \"id\"}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        InvocationRequest request = InvocationRequest.deserialize(nodes);

        assertThat(request.getMethodName()).isEqualTo(Method.Request.name("getAccounts"));
        assertThat(request.getParameters()).isNotNull();
        assertThat(request.getMethodCallId()).isEqualTo(MethodCallId.of("#1"));
    }
}
