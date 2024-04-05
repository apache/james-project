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

package org.apache.james.jmap.model;

import org.apache.james.jmap.methods.Method;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;

public class InvocationResponse {

    private final Method.Response.Name name;
    private final ObjectNode results;
    private final MethodCallId methodCallId;

    public InvocationResponse(Method.Response.Name name, ObjectNode results, MethodCallId methodCallId) {
        Preconditions.checkNotNull(name, "method is mandatory");
        Preconditions.checkNotNull(results, "results is mandatory");
        Preconditions.checkNotNull(methodCallId,  "methodCallId is mandatory");
        this.name = name;
        this.results = results;
        this.methodCallId = methodCallId;
    }

    public Method.Response.Name getResponseName() {
        return name;
    }

    public ObjectNode getResults() {
        return results;
    }

    public MethodCallId getMethodCallId() {
        return methodCallId;
    }

    public Object[] asProtocolSpecification() {
        return new Object[] { getResponseName(), getResults(), getMethodCallId() };
    }
}