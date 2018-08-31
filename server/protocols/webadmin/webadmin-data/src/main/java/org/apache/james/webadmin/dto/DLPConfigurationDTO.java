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

package org.apache.james.webadmin.dto;

import java.util.List;
import java.util.stream.Stream;

import org.apache.james.dlp.api.DLPConfigurationItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class DLPConfigurationDTO {

    public static DLPConfigurationDTO toDTO(Stream<DLPConfigurationItem> dlpConfigurations) {
        Preconditions.checkNotNull(dlpConfigurations);

        return new DLPConfigurationDTO(
            dlpConfigurations.map(DLPConfigurationItemDTO::toDTO)
                .collect(Guavate.toImmutableList()));
    }

    private final ImmutableList<DLPConfigurationItemDTO> rules;

    @JsonCreator
    public DLPConfigurationDTO(
            @JsonProperty("rules") ImmutableList<DLPConfigurationItemDTO> rules) {
        this.rules = rules;
    }

    public ImmutableList<DLPConfigurationItemDTO> getRules() {
        return rules;
    }

    @JsonIgnore
    public List<DLPConfigurationItem> toDLPConfigurations() {
        return rules.stream()
            .map(DLPConfigurationItemDTO::toDLPConfiguration)
            .collect(Guavate.toImmutableList());
    }
}
