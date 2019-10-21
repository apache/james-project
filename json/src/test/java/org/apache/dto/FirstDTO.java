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

package org.apache.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.json.DTO;
import org.apache.james.json.DTOConverter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FirstDTO implements DTO {
    private final String type;
    private final Optional<Long> id;
    private final String time;
    private final String payload;
    private final Optional<DTO> child;

    @JsonCreator
    public FirstDTO(
            @JsonProperty("type") String type,
            @JsonProperty("id") Optional<Long> id,
            @JsonProperty("time") String time,
            @JsonProperty("payload") String payload,
            @JsonProperty("child") Optional<DTO> child) {
        this.type = type;
        this.id = id;
        this.time = time;
        this.payload = payload;
        this.child = child;
    }

    public String getType() {
        return type;
    }

    public Optional<Long> getId() {
        return id;
    }

    public String getTime() {
        return time;
    }

    public String getPayload() {
        return payload;
    }

    public Optional<DTO> getChild() {
        return child;
    }

    @JsonIgnore
    public FirstDomainObject toDomainObject(DTOConverter<NestedType, DTO> converter) {
        return new FirstDomainObject(id, ZonedDateTime.parse(time), payload, child.flatMap(converter::convert));
    }
}
