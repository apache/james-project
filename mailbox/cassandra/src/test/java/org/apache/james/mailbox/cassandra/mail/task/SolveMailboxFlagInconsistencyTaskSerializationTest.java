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

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistenciesService.TargetFlag;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxFlagInconsistencyTask.Details;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class SolveMailboxFlagInconsistencyTaskSerializationTest {

    private static final SolveMailboxFlagInconsistenciesService SERVICE = mock(SolveMailboxFlagInconsistenciesService.class);

    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");

    @Test
    void taskShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SolveMailboxFlagInconsistenciesTaskDTO.module(SERVICE))
            .bean(new SolveMailboxFlagInconsistencyTask(SERVICE, TargetFlag.RECENT))
            .json("{" +
                "  \"type\":\"solve-mailbox-flag-inconsistencies\"," +
                "  \"flagName\":\"RECENT\"" +
                "}")
            .verify();
    }

    @Test
    void additionalInformationShouldBeSerializable() throws Exception {
        JsonSerializationVerifier.dtoModule(SolveMailboxFlagInconsistenciesTaskAdditionalInformationDTO.module())
            .bean(new Details(INSTANT, 2,
                ImmutableList.of("d2bee791-7e63-11ea-883c-95b84008f979", "ffffffff-7e63-11ea-883c-95b84008f979"),
                TargetFlag.DELETED.name()))
            .json("{" +
                "    \"errors\": [\"d2bee791-7e63-11ea-883c-95b84008f979\", \"ffffffff-7e63-11ea-883c-95b84008f979\"]," +
                "    \"processedMailboxEntries\": 2," +
                "    \"timestamp\": \"2007-12-03T10:15:30Z\"," +
                "    \"targetFlag\": \"DELETED\"," +
                "    \"type\": \"solve-mailbox-flag-inconsistencies\"" +
                "}")
            .verify();
    }
}
