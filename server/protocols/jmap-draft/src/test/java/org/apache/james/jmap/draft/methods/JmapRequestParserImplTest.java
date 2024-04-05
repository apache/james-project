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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.draft.model.InvocationRequest;
import org.apache.james.jmap.json.ObjectMapperFactory;
import org.apache.james.jmap.methods.JmapRequest;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class JmapRequestParserImplTest {
    private JmapRequestParserImpl testee;

    @Before
    public void setup() {
        testee = new JmapRequestParserImpl(new ObjectMapperFactory(new InMemoryId.Factory(), new InMemoryMessageId.Factory()));
    }
    
    @Test
    public void extractJmapRequestShouldThrowWhenNullRequestClass() {
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                new ObjectNode(new JsonNodeFactory(false)).putObject("{\"id\": \"id\"}"),
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        assertThatThrownBy(() -> testee.extractJmapRequest(InvocationRequest.deserialize(nodes), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void extractJmapRequestShouldNotThrowWhenJsonContainsUnknownProperty() throws Exception {
        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        parameters.put("id", "myId");
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        testee.extractJmapRequest(InvocationRequest.deserialize(nodes), RequestClass.class);
    }

    @Test
    public void extractJmapRequestShouldNotThrowWhenPropertyMissingInJson() throws Exception {
        ObjectNode parameters = new ObjectNode(new JsonNodeFactory(false));
        JsonNode[] nodes = new JsonNode[] { new ObjectNode(new JsonNodeFactory(false)).textNode("unknwonMethod"),
                parameters,
                new ObjectNode(new JsonNodeFactory(false)).textNode("#1")};

        testee.extractJmapRequest(InvocationRequest.deserialize(nodes), RequestClass.class);
    }

    private static class RequestClass implements JmapRequest {

        @SuppressWarnings("unused")
        public String parameter;
    
    }
}
