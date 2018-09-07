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
import java.util.Objects;

import org.apache.mailet.PerRecipientHeaders;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;

public class HeadersDto {

    static HeadersDto from(Collection<PerRecipientHeaders.Header> headers) {
        return new HeadersDto(headers.stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                PerRecipientHeaders.Header::getName,
                PerRecipientHeaders.Header::getValue)));
    }

    private final Multimap<String, String> headers;

    @JsonCreator
    private HeadersDto(@JsonProperty("header") Multimap<String, String> headers) {
        this.headers = headers;
    }

    @JsonProperty("header")
    public Multimap<String, String> getHeaders() {
        return headers;
    }

    Collection<PerRecipientHeaders.Header> toHeaders() {
        return headers.entries()
            .stream()
            .map(entry -> PerRecipientHeaders.Header.builder()
                .name(entry.getKey())
                .value(entry.getValue())
                .build())
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof HeadersDto) {
            HeadersDto that = (HeadersDto) o;

            return Objects.equals(this.headers, that.headers);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(headers);
    }
}
