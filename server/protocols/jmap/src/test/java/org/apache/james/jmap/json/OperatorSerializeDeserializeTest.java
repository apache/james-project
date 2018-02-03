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

package org.apache.james.jmap.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.jmap.model.Operator;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

public class OperatorSerializeDeserializeTest {

    @Test
    public void deserializeKnownValue() throws Exception {
        ObjectWithOperator operator = new ObjectMapper().readValue("{\"operator\":\"AND\"}", ObjectWithOperator.class);
        assertThat(operator.operator).isEqualTo(Operator.AND);
    }

    @Test(expected = InvalidFormatException.class)
    public void deserializeUnknownValue() throws Exception {
        new ObjectMapper().readValue("{\"operator\":\"UNKNOWN\"}", ObjectWithOperator.class);
    }

    @Test
    public void serializeKnownValue() throws Exception {
        ObjectWithOperator objectWithOperator = new ObjectWithOperator();
        objectWithOperator.operator = Operator.AND;
        String operator = new ObjectMapper().writeValueAsString(objectWithOperator);
        assertThat(operator).isEqualTo("{\"operator\":\"AND\"}");
    }

    private static class ObjectWithOperator {

        public Operator operator;
    }
}
