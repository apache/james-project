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
package org.apache.james.jmap.draft.json;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MultipleObjectMapperTest {

    private ObjectMapper mapper;

    @SuppressWarnings("unused")
    private static class First {
        public String first;
        public String other;
    }

    @SuppressWarnings("unused")
    private static class Second {
        public String second;
        public String other;
    }

    @Before
    public void setup() {
        mapper = new MultipleObjectMapperBuilder()
                    .registerClass("/first", First.class)
                    .registerClass("/second", Second.class)
                    .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void registeringSameUniquePathShouldThrowAnException() throws Exception {
        new MultipleObjectMapperBuilder()
            .registerClass("/other", First.class)
            .registerClass("/other", Second.class);
    }

    @Test
    public void registeringSameClassTwoTimesIsOK() throws Exception {
        ObjectMapper uselessMapper = new MultipleObjectMapperBuilder()
                .registerClass("/first", First.class)
                .registerClass("/other", First.class)
                .build();
        String json = "{\"first\": \"value\", \"other\": \"other\"}";
        Object o = uselessMapper.readValue(json, Object.class);
        assertThat(o).isInstanceOf(First.class);
    }

    @Test(expected = JsonMappingException.class)
    public void badJsonShouldThrowException() throws Exception {
        String json = "{\"bad\": \"value\"}";
        mapper.readValue(json, Object.class);
    }

    @Test
    public void firstJsonShouldReturnFirstClass() throws Exception {
        String json = "{\"first\": \"value\", \"other\": \"other\"}";
        Object o = mapper.readValue(json, Object.class);
        assertThat(o).isInstanceOf(First.class);
    }

    @Test
    public void secondJsonShouldReturnSecondClass() throws Exception {
        String json = "{\"second\": \"value\", \"other\": \"other\"}";
        Object o = mapper.readValue(json, Object.class);
        assertThat(o).isInstanceOf(Second.class);
    }
}
