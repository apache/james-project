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

import org.apache.james.jmap.methods.Method;
import org.apache.james.jmap.model.InvocationResponse;
import org.apache.james.jmap.model.MethodCallId;
import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class InvocationResponseTest {

    @Test(expected = NullPointerException.class)
    public void newInstanceShouldThrowWhenMethodIsNull() {
        new InvocationResponse(null, new ObjectNode(JsonNodeFactory.instance), MethodCallId.of("id"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void newInstanceShouldThrowWhenMethodIsEmpty() {
        new InvocationResponse(Method.Response.name(""), new ObjectNode(JsonNodeFactory.instance), MethodCallId.of("id"));
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceShouldThrowWhenResultsIsNull() {
        new InvocationResponse(Method.Response.name("method"), null, MethodCallId.of("id"));
    }

    @Test(expected = NullPointerException.class)
    public void newInstanceShouldThrowWhenMethodCallIdIsNull() {
        new InvocationResponse(Method.Response.name("method"), new ObjectNode(new JsonNodeFactory(false)).putObject("{}"), null);
    }

    @Test
    public void asProtocolSpecificationShouldReturnAnArrayWithThreeElements() {
        Object[] asProtocolSpecification = new InvocationResponse(Method.Response.name("method"), new ObjectNode(new JsonNodeFactory(false)).putObject("{}"), MethodCallId.of("#1"))
                .asProtocolSpecification();

        assertThat(asProtocolSpecification).hasSize(3);
    }
}
