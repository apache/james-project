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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.quota.task.RecomputeCurrentQuotasService.RunningOptions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class RecomputeCurrentQuotasTaskSerializationTest {
    static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    static final String QUOTA_ROOT_AS_STRING = "bob@localhost";

    static final RecomputeCurrentQuotasService SERVICE = mock(RecomputeCurrentQuotasService.class);
    static final RecomputeCurrentQuotasTask TASK = new RecomputeCurrentQuotasTask(SERVICE, RunningOptions.of(17, ImmutableList.of(QuotaComponent.JMAP_UPLOADS)));
    static final RecomputeCurrentQuotasTask TASK_DEFAULT = new RecomputeCurrentQuotasTask(SERVICE, RunningOptions.DEFAULT);
    static final String SERIALIZED_TASK_LEGACY = "{\"type\": \"recompute-current-quotas\"}";
    static final String SERIALIZED_TASK = "{\"type\": \"recompute-current-quotas\",\"runningOptions\":{\"usersPerSecond\":17,\"quotaComponents\":[\"JMAP_UPLOADS\"]}}";
    static final RecomputeCurrentQuotasTask.Details DETAILS = new RecomputeCurrentQuotasTask.Details(TIMESTAMP, 12,
        ImmutableList.of(QUOTA_ROOT_AS_STRING), RunningOptions.of(17, ImmutableList.of(QuotaComponent.JMAP_UPLOADS)));
    static final RecomputeCurrentQuotasTask.Details DETAILS_DEFAULT = new RecomputeCurrentQuotasTask.Details(TIMESTAMP, 12, ImmutableList.of(QUOTA_ROOT_AS_STRING), RunningOptions.DEFAULT);
    static final String SERIALIZED_ADDITIONAL_INFORMATION_LEGACY = "{" +
        "  \"type\":\"recompute-current-quotas\"," +
        "  \"processedQuotaRoots\":12," +
        "  \"failedQuotaRoots\":[\"bob@localhost\"]," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";
    static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "  \"type\":\"recompute-current-quotas\"," +
        "  \"processedQuotaRoots\":12," +
        "  \"failedQuotaRoots\":[\"bob@localhost\"]," +
        "  \"runningOptions\":{\"usersPerSecond\":17,\"quotaComponents\":[\"JMAP_UPLOADS\"]}," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeCurrentQuotasTaskDTO.module(SERVICE))
            .bean(TASK)
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeCurrentQuotasTaskAdditionalInformationDTO.module())
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }

    @Test
    void shouldDeserializeLegacyTask() throws Exception {
        RecomputeCurrentQuotasTask legacyTask = JsonGenericSerializer.forModules(RecomputeCurrentQuotasTaskDTO.module(SERVICE))
            .withoutNestedType()
            .deserialize(SERIALIZED_TASK_LEGACY);

        assertThat(legacyTask)
            .isEqualToComparingFieldByFieldRecursively(TASK_DEFAULT);
    }

    @Test
    void shouldDeserializeLegacyDetails() throws Exception {
        RecomputeCurrentQuotasTask.Details legacyDetails = JsonGenericSerializer.forModules(RecomputeCurrentQuotasTaskAdditionalInformationDTO.module())
            .withoutNestedType()
            .deserialize(SERIALIZED_ADDITIONAL_INFORMATION_LEGACY);

        assertThat(legacyDetails)
            .isEqualToComparingFieldByFieldRecursively(DETAILS_DEFAULT);
    }
}
