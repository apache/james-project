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

package org.apache.james.dlp.eventsourcing.cassandra;

import static com.github.steveash.guavate.Guavate.toImmutableList;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.dlp.api.DLPConfigurationItem;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public class DLPConfigurationItemDTO {

    public static DLPConfigurationItemDTO from(DLPConfigurationItem dlpConfiguration) {
        DLPConfigurationItem.Targets targets = dlpConfiguration.getTargets();

        return new DLPConfigurationItemDTO(
            dlpConfiguration.getId().asString(),
            dlpConfiguration.getExplanation(),
            dlpConfiguration.getRegexp().pattern(),
            targets.isContentTargeted(),
            targets.isSenderTargeted(),
            targets.isRecipientTargeted());
    }

    public static List<DLPConfigurationItemDTO> from(List<DLPConfigurationItem> configurationItems) {
        Preconditions.checkNotNull(configurationItems);

        return configurationItems
            .stream()
            .map(DLPConfigurationItemDTO::from)
            .collect(toImmutableList());
    }

    public static List<DLPConfigurationItem> fromDTOs(List<DLPConfigurationItemDTO> configurationItems) {
        Preconditions.checkNotNull(configurationItems);

        return configurationItems
            .stream()
            .map(DLPConfigurationItemDTO::toDLPConfiguration)
            .collect(toImmutableList());
    }

    private final String id;
    private final Optional<String> explanation;
    private final String expression;
    private final boolean targetsContent;
    private final boolean targetsSender;
    private final boolean targetsRecipients;

    @JsonCreator
    private DLPConfigurationItemDTO(@JsonProperty("id") String id,
                                   @JsonProperty("explanation") Optional<String> explanation,
                                   @JsonProperty("expression") String expression,
                                   @JsonProperty("targetsContent") boolean targetsContent,
                                   @JsonProperty("targetsSender") boolean targetsSender,
                                   @JsonProperty("targetsRecipients") boolean targetsRecipients) {
        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(expression);

        this.id = id;
        this.explanation = explanation;
        this.expression = expression;
        this.targetsContent = targetsContent;
        this.targetsSender = targetsSender;
        this.targetsRecipients = targetsRecipients;
    }

    public String getId() {
        return id;
    }

    public Optional<String> getExplanation() {
        return explanation;
    }

    public String getExpression() {
        return expression;
    }

    public boolean isTargetsContent() {
        return targetsContent;
    }

    public boolean isTargetsSender() {
        return targetsSender;
    }

    public boolean isTargetsRecipients() {
        return targetsRecipients;
    }

    @JsonIgnore
    public DLPConfigurationItem toDLPConfiguration() {
        return DLPConfigurationItem.builder()
            .id(DLPConfigurationItem.Id.of(id))
            .expression(expression)
            .explanation(explanation)
            .targetsSender(targetsSender)
            .targetsContent(targetsContent)
            .targetsRecipients(targetsRecipients)
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof DLPConfigurationItemDTO) {
            DLPConfigurationItemDTO that = (DLPConfigurationItemDTO) o;

            return Objects.equals(this.targetsContent, that.targetsContent)
                && Objects.equals(this.targetsSender, that.targetsSender)
                && Objects.equals(this.targetsRecipients, that.targetsRecipients)
                && Objects.equals(this.explanation, that.explanation)
                && Objects.equals(this.expression, that.expression)
                && Objects.equals(this.id, that.id);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(id, explanation, expression, targetsContent, targetsSender, targetsRecipients);
    }
}
