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
import java.util.Objects;
import java.util.Optional;

import org.apache.james.dlp.api.DLPConfigurationItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class DLPConfigurationItemDTO {

    @VisibleForTesting
    static DLPConfigurationItem toDLPConfiguration(DLPConfigurationItemDTO dto) {
        return DLPConfigurationItem.builder()
            .id(DLPConfigurationItem.Id.of(dto.id))
            .expression(dto.expression)
            .explanation(dto.explanation)
            .targetsSender(dto.targetsSender)
            .targetsRecipients(dto.targetsRecipients)
            .targetsContent(dto.targetsContent)
            .build();
    }

    @VisibleForTesting
    static DLPConfigurationItemDTO toDTO(DLPConfigurationItem dlpConfiguration) {
        DLPConfigurationItem.Targets targets = dlpConfiguration.getTargets();
        return new DLPConfigurationItemDTO(
            dlpConfiguration.getId().asString(),
            dlpConfiguration.getRegexp(),
            dlpConfiguration.getExplanation(),
            Optional.of(targets.isSenderTargeted()),
            Optional.of(targets.isRecipientTargeted()),
            Optional.of(targets.isContentTargeted()));
    }

    private final String id;
    private final String expression;
    private final Optional<String> explanation;
    private final Optional<Boolean> targetsSender;
    private final Optional<Boolean> targetsRecipients;
    private final Optional<Boolean> targetsContent;

    @JsonCreator
    public DLPConfigurationItemDTO(@JsonProperty("id") String id,
                                   @JsonProperty("expression") String expression,
                                   @JsonProperty("explanation") Optional<String> explanation,
                                   @JsonProperty("targetsSender") Optional<Boolean> targetsSender,
                                   @JsonProperty("targetsRecipients") Optional<Boolean> targetsRecipients,
                                   @JsonProperty("targetsContent") Optional<Boolean> targetsContent) {
        Preconditions.checkNotNull(id, "'id' is mandatory");
        Preconditions.checkNotNull(expression, "'expression' is mandatory");
        this.id = id;
        this.expression = expression;
        this.explanation = explanation;
        this.targetsSender = targetsSender;
        this.targetsRecipients = targetsRecipients;
        this.targetsContent = targetsContent;
    }

    public String getId() {
        return id;
    }

    public String getExpression() {
        return expression;
    }

    public Optional<String> getExplanation() {
        return explanation;
    }

    public Optional<Boolean> getTargetsSender() {
        return targetsSender;
    }

    public Optional<Boolean> getTargetsRecipients() {
        return targetsRecipients;
    }

    public Optional<Boolean> getTargetsContent() {
        return targetsContent;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DLPConfigurationItemDTO) {
            DLPConfigurationItemDTO that = (DLPConfigurationItemDTO) o;

            return Objects.equals(this.id, that.id)
                && Objects.equals(this.expression, that.expression)
                && Objects.equals(this.explanation, that.explanation)
                && Objects.equals(this.targetsSender, that.targetsSender)
                && Objects.equals(this.targetsRecipients, that.targetsRecipients)
                && Objects.equals(this.targetsContent, that.targetsContent);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, expression, explanation, targetsSender, targetsRecipients, targetsContent);
    }
}
