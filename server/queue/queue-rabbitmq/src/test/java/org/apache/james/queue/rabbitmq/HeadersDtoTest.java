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

package org.apache.james.queue.rabbitmq;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.mailet.PerRecipientHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;

class HeadersDtoTest {
    private static final List<PerRecipientHeaders.Header> INTERLEAVED_HEADERS = ImmutableList.of(
        header("A", "1"),
        header("B", "2"),
        header("A", "3"));
    private static final List<PerRecipientHeaders.Header> GROUPED_HEADERS = ImmutableList.of(
        header("A", "1"),
        header("A", "3"),
        header("B", "2"));

    private static PerRecipientHeaders.Header header(String name, String value) {
        return PerRecipientHeaders.Header.builder().name(name).value(value).build();
    }

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new GuavaModule());
    }

    @Test
    void toHeadersShouldPreserveInsertionOrder() {
        assertThat(HeadersDto.from(INTERLEAVED_HEADERS).toHeaders())
            .containsExactlyElementsOf(INTERLEAVED_HEADERS);
    }

    @Test
    void toHeadersShouldPreserveInsertionOrderWhenGroupedByName() {
        assertThat(HeadersDto.from(GROUPED_HEADERS).toHeaders())
            .containsExactlyElementsOf(GROUPED_HEADERS);
    }

    @Test
    void interleavedHeadersShouldPreserveTheirOrderThroughJson() throws Exception {
        String json = objectMapper.writeValueAsString(HeadersDto.from(INTERLEAVED_HEADERS));

        assertThat(objectMapper.readValue(json, HeadersDto.class).toHeaders())
            .containsExactlyElementsOf(INTERLEAVED_HEADERS);
    }

    @Test
    void interleavedHeadersShouldBeSerializedAsAnOrderedList() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(HeadersDto.from(INTERLEAVED_HEADERS)))
            .isEqualTo("{\"headers\":[" +
                "{\"name\":\"A\",\"value\":\"1\"}," +
                "{\"name\":\"B\",\"value\":\"2\"}," +
                "{\"name\":\"A\",\"value\":\"3\"}]}");
    }

    @Test
    void headersGroupedByNameShouldKeepTheHistoricalFormat() throws Exception {
        assertThatJson(objectMapper.writeValueAsString(HeadersDto.from(GROUPED_HEADERS)))
            .isEqualTo("{\"header\":{\"A\":[\"1\",\"3\"],\"B\":[\"2\"]}}");
    }

    @Test
    void historicalFormatShouldBeDeserialized() throws Exception {
        HeadersDto dto = objectMapper.readValue("{\"header\":{\"A\":[\"1\",\"3\"],\"B\":[\"2\"]}}", HeadersDto.class);

        assertThat(dto.toHeaders()).containsExactlyElementsOf(GROUPED_HEADERS);
    }
}
