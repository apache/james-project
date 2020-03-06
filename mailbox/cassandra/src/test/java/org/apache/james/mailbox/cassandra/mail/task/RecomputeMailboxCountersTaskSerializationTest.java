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

import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.core.Username;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class RecomputeMailboxCountersTaskSerializationTest {
    private static final Instant TIMESTAMP = Instant.parse("2018-11-13T12:00:55Z");
    private static final String MAILBOX_ID_AS_STRING = "464765a0-e4e7-11e4-aba4-710c1de3782b";

    private static final RecomputeMailboxCountersService SERVICE = mock(RecomputeMailboxCountersService.class);
    private static final RecomputeMailboxCountersTask TASK = new RecomputeMailboxCountersTask(SERVICE);
    private static final String SERIALIZED_TASK = "{\"type\": \"recompute-mailbox-counters\"}";
    private static final RecomputeMailboxCountersTask.Details DETAILS = new RecomputeMailboxCountersTask.Details(TIMESTAMP, 12, ImmutableList.of(MAILBOX_ID_AS_STRING));
    private static final String SERIALIZED_ADDITIONAL_INFORMATION = "{" +
        "  \"type\":\"recompute-mailbox-counters\"," +
        "  \"processedMailboxes\":12," +
        "  \"failedMailboxes\":[\"464765a0-e4e7-11e4-aba4-710c1de3782b\"]," +
        "  \"timestamp\":\"2018-11-13T12:00:55Z\"" +
        "}";

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeMailboxCountersTaskDTO.module(SERVICE))
            .bean(TASK)
            .json(SERIALIZED_TASK)
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeMailboxCountersTaskAdditionalInformationDTO.MODULE)
            .bean(DETAILS)
            .json(SERIALIZED_ADDITIONAL_INFORMATION)
            .verify();
    }
}