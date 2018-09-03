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

import java.util.Objects;
import java.util.Optional;

import org.apache.james.dlp.api.DLPConfigurationItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public class DLPConfigurationItemDTO {

    public static DLPConfigurationItemDTO toDTO(DLPConfigurationItem dlpConfiguration) {
        DLPConfigurationItem.Targets targets = dlpConfiguration.getTargets();
        return new DLPConfigurationItemDTO(
            dlpConfiguration.getId().asString(),
            dlpConfiguration.getRegexp().pattern(),
            dlpConfiguration.getExplanation(),
            targets.isSenderTargeted(),
            targets.isRecipientTargeted(),
            targets.isContentTargeted());
    }



    private final String id;
    private final String expression;
    private final Optional<String> explanation;
    private final boolean targetsSender;
    private final boolean targetsRecipients;
    private final boolean targetsContent;

    @JsonCreator
    public DLPConfigurationItemDTO(@JsonProperty("id") String id,
                                   @JsonProperty("expression") String expression,
                                   @JsonProperty("explanation") Optional<String> explanation,
                                   @JsonProperty("targetsSender") boolean targetsSender,
                                   @JsonProperty("targetsRecipients") boolean targetsRecipients,
                                   @JsonProperty("targetsContent") boolean targetsContent) {
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

    public boolean getTargetsSender() {
        return targetsSender;
    }

    public boolean getTargetsRecipients() {
        return targetsRecipients;
    }

    public boolean getTargetsContent() {
        return targetsContent;
    }

    @JsonIgnore
    public DLPConfigurationItem toDLPConfiguration() {
        return DLPConfigurationItem.builder()
            .id(DLPConfigurationItem.Id.of(id))
            .expression(expression)
            .explanation(explanation)
            .targetsSender(targetsSender)
            .targetsRecipients(targetsRecipients)
            .targetsContent(targetsContent)
            .build();
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
