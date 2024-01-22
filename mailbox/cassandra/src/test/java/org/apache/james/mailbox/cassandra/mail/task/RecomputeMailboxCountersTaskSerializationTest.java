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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.JsonSerializationVerifier.recursiveComparisonConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.json.JsonGenericSerializer;
import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService.Options;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class RecomputeMailboxCountersTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final String MAILBOX_ID_AS_STRING = "464765a0-e4e7-11e4-aba4-710c1de3782b";

    private static final RecomputeMailboxCountersService SERVICE = mock(RecomputeMailboxCountersService.class);
    private static final RecomputeMailboxCountersTask TASK_TRUSTED = new RecomputeMailboxCountersTask(SERVICE, Options.trustMessageProjection());
    private static final RecomputeMailboxCountersTask TASK_UNTRUSTED = new RecomputeMailboxCountersTask(SERVICE, Options.recheckMessageProjection());
    private static final String SERIALIZED_TASK_TRUSTED = "{" +
        "  \"type\": \"recompute-mailbox-counters\"," +
        "  \"trustMessageProjection\": true" +
        "}";
    private static final String SERIALIZED_TASK_UNTRUSTED = "{" +
        "  \"type\": \"recompute-mailbox-counters\"," +
        "  \"trustMessageProjection\": false" +
        "}";
    private static final String SERIALIZED_TASK_OLD = "{\"type\": \"recompute-mailbox-counters\"}";
    private static final RecomputeMailboxCountersTask.Details DETAILS = new RecomputeMailboxCountersTask.Details(TIMESTAMP, 12, ImmutableList.of(MAILBOX_ID_AS_STRING));
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "  \"type\":\"recompute-mailbox-counters\"," +
        "  \"processedMailboxes\":12," +
        "  \"failedMailboxes\":[\"464765a0-e4e7-11e4-aba4-710c1de3782b\"]," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";

    @Test
    void taskShouldBeSerializableWhenTrusted() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeMailboxCountersTaskDTO.module(SERVICE))
            .bean(TASK_TRUSTED)
            .json(SERIALIZED_TASK_TRUSTED)
            .verify();
    }

    @Test
    void taskShouldBeSerializableWhenUnTrusted() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeMailboxCountersTaskDTO.module(SERVICE))
            .bean(TASK_UNTRUSTED)
            .json(SERIALIZED_TASK_UNTRUSTED)
            .verify();
    }

    @Test
    void taskWithoutTrustFieldShouldBeWellDeSerialized() throws Exception {
        RecomputeMailboxCountersTask domainObject = JsonGenericSerializer
            .forModules(RecomputeMailboxCountersTaskDTO.module(SERVICE))
            .withoutNestedType()
            .deserialize(SERIALIZED_TASK_OLD);

        assertThat(domainObject)
            .usingRecursiveComparison(recursiveComparisonConfiguration)
            .isEqualTo(TASK_UNTRUSTED);
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeMailboxCountersTaskAdditionalInformationDTO.module())
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}