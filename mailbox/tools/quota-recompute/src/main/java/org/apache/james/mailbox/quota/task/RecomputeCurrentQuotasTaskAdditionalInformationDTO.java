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

package org.apache.james.mailbox.quota.task;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

public class RecomputeCurrentQuotasTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    static class RecomputeSingleQuotaComponentResultDTO {
        private final String quotaComponent;
        private final long processedIdentifierCount;
        private final ImmutableList<String> failedIdentifiers;

        public RecomputeSingleQuotaComponentResultDTO(@JsonProperty("quotaComponent") String quotaComponent,
                                                      @JsonProperty("processedQuotaRoots") long processedIdentifierCount,
                                                      @JsonProperty("failedQuotaRoots") ImmutableList<String> failedIdentifiers) {
            this.quotaComponent = quotaComponent;
            this.processedIdentifierCount = processedIdentifierCount;
            this.failedIdentifiers = failedIdentifiers;
        }

        public String getQuotaComponent() {
            return quotaComponent;
        }

        public long getProcessedIdentifierCount() {
            return processedIdentifierCount;
        }

        public ImmutableList<String> getFailedIdentifiers() {
            return failedIdentifiers;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof RecomputeSingleQuotaComponentResultDTO) {
                RecomputeSingleQuotaComponentResultDTO that = (RecomputeSingleQuotaComponentResultDTO) o;

                return Objects.equals(this.quotaComponent, that.quotaComponent)
                    && Objects.equals(this.processedIdentifierCount, that.processedIdentifierCount)
                    && Objects.equals(this.failedIdentifiers, that.failedIdentifiers);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(quotaComponent, processedIdentifierCount, failedIdentifiers);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("quotaComponent", quotaComponent)
                .add("processedIdentifierCount", processedIdentifierCount)
                .add("failedIdentifiers", failedIdentifiers)
                .toString();
        }
    }

    private static RecomputeCurrentQuotasTaskAdditionalInformationDTO fromDomainObject(RecomputeCurrentQuotasTask.Details details, String type) {
        return new RecomputeCurrentQuotasTaskAdditionalInformationDTO(
            type,
            details.getResults().stream()
                .map(recomputeSingleQuotaComponentResult -> new RecomputeSingleQuotaComponentResultDTO(recomputeSingleQuotaComponentResult.getQuotaComponent(),
                    recomputeSingleQuotaComponentResult.getProcessedIdentifierCount(),
                    recomputeSingleQuotaComponentResult.getFailedIdentifiers()))
                .collect(Collectors.toUnmodifiableList()),
            Optional.of(RunningOptionsDTO.asDTO(details.getRunningOptions())),
            details.timestamp());
    }

    public static AdditionalInformationDTOModule<RecomputeCurrentQuotasTask.Details, RecomputeCurrentQuotasTaskAdditionalInformationDTO> module() {
        return DTOModule.forDomainObject(RecomputeCurrentQuotasTask.Details.class)
            .convertToDTO(RecomputeCurrentQuotasTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(RecomputeCurrentQuotasTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(RecomputeCurrentQuotasTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(RecomputeCurrentQuotasTask.RECOMPUTE_CURRENT_QUOTAS.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private final String type;
    private final List<RecomputeSingleQuotaComponentResultDTO> recomputeSingleQuotaComponentResults;
    private final Optional<RunningOptionsDTO> runningOptions;
    private final Instant timestamp;

    public RecomputeCurrentQuotasTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                              @JsonProperty("recomputeSingleQuotaComponentResults") List<RecomputeSingleQuotaComponentResultDTO> recomputeSingleQuotaComponentResults,
                                                              @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                              @JsonProperty("timestamp") Instant timestamp) {
        this.type = type;
        this.recomputeSingleQuotaComponentResults = recomputeSingleQuotaComponentResults;
        this.runningOptions = runningOptions;
        this.timestamp = timestamp;
    }

    public List<RecomputeSingleQuotaComponentResultDTO> getRecomputeSingleQuotaComponentResults() {
        return recomputeSingleQuotaComponentResults;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String getType() {
        return type;
    }

    public Optional<RunningOptionsDTO> getRunningOptions() {
        return runningOptions;
    }

    private RecomputeCurrentQuotasTask.Details toDomainObject() {
        return new RecomputeCurrentQuotasTask.Details(timestamp,
            recomputeSingleQuotaComponentResults.stream()
                .map(recomputeSingleQuotaComponentResultDTO -> new RecomputeSingleQuotaComponentResult(recomputeSingleQuotaComponentResultDTO.getQuotaComponent(),
                    recomputeSingleQuotaComponentResultDTO.getProcessedIdentifierCount(),
                    recomputeSingleQuotaComponentResultDTO.getFailedIdentifiers()))
                .collect(Collectors.toUnmodifiableList()),
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT));
    }
}
