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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.mailet.PerRecipientHeaders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

/**
 * Per recipient headers of a recipient.
 *
 * The historical "header" form groups the values by header name, and thus loses the relative order of interleaved
 * header names (A, B, A). The "headers" form, a list of name/value pairs, is only written when needed, so that the
 * JSON stays readable by nodes that only know the historical form whenever possible.
 */
public class HeadersDto {
    record HeaderDto(@JsonProperty("name") String name, @JsonProperty("value") String value) {
        static HeaderDto from(PerRecipientHeaders.Header header) {
            return new HeaderDto(header.getName(), header.getValue());
        }

        PerRecipientHeaders.Header toHeader() {
            return PerRecipientHeaders.Header.builder()
                .name(name)
                .value(value)
                .build();
        }
    }

    static HeadersDto from(Collection<PerRecipientHeaders.Header> headers) {
        ImmutableListMultimap<String, String> groupedByName = headers.stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                PerRecipientHeaders.Header::getName,
                PerRecipientHeaders.Header::getValue));

        if (toHeaders(groupedByName).equals(ImmutableList.copyOf(headers))) {
            return new HeadersDto(groupedByName, null);
        }
        return new HeadersDto(null, headers.stream()
            .map(HeaderDto::from)
            .collect(ImmutableList.toImmutableList()));
    }

    private static List<PerRecipientHeaders.Header> toHeaders(Multimap<String, String> groupedByName) {
        return groupedByName.entries()
            .stream()
            .map(entry -> PerRecipientHeaders.Header.builder()
                .name(entry.getKey())
                .value(entry.getValue())
                .build())
            .collect(ImmutableList.toImmutableList());
    }

    private final Optional<Multimap<String, String>> headers;
    private final Optional<List<HeaderDto>> orderedHeaders;

    @JsonCreator
    private HeadersDto(@JsonProperty("header") Multimap<String, String> headers,
                       @JsonProperty("headers") List<HeaderDto> orderedHeaders) {
        this.headers = Optional.ofNullable(headers);
        this.orderedHeaders = Optional.ofNullable(orderedHeaders);
    }

    @JsonProperty("header")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<Multimap<String, String>> getHeaders() {
        return headers;
    }

    @JsonProperty("headers")
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public Optional<List<HeaderDto>> getOrderedHeaders() {
        return orderedHeaders;
    }

    Collection<PerRecipientHeaders.Header> toHeaders() {
        return orderedHeaders
            .<List<PerRecipientHeaders.Header>>map(list -> list.stream()
                .map(HeaderDto::toHeader)
                .collect(ImmutableList.toImmutableList()))
            .orElseGet(() -> headers
                .map(HeadersDto::toHeaders)
                .orElse(ImmutableList.of()));
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof HeadersDto) {
            HeadersDto that = (HeadersDto) o;

            return Objects.equals(this.headers, that.headers)
                && Objects.equals(this.orderedHeaders, that.orderedHeaders);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(headers, orderedHeaders);
    }
}
