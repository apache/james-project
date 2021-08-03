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

package org.apache.james.pop3server.mailbox.task;

import java.time.Instant;

import org.apache.james.JsonSerializationVerifier;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class MetaDataFixInconsistenciesAdditionalInformationDTOTest {
    private static final Instant INSTANT = Instant.parse("2007-12-03T10:15:30.00Z");
    private static final String JSON = "{" +
        "  \"timestamp\":\"2007-12-03T10:15:30Z\"," +
        "  \"type\":\"Pop3MetaDataFixInconsistenciesTask\"," +
        "  \"runningOptions\":{\"messagesPerSecond\":36}," +
        "  \"processedImapUidEntries\":12," +
        "  \"processedPop3MetaDataStoreEntries\":13," +
        "  \"stalePOP3Entries\":14," +
        "  \"missingPOP3Entries\":15," +
        "  \"fixedInconsistencies\":[" +
        "     {\"mailboxId\":\"a\",\"messageId\":\"b\"}," +
        "     {\"mailboxId\":\"c\",\"messageId\":\"d\"}" +
        "  ]," +
        "  \"errors\":[" +
        "     {\"mailboxId\":\"e\",\"messageId\":\"f\"}," +
        "     {\"mailboxId\":\"g\",\"messageId\":\"h\"}" +
        "  ]" +
        "}";

    @Test
    public void shouldMatchSerializableContract() throws Exception {
        JsonSerializationVerifier.dtoModule(MetaDataFixInconsistenciesAdditionalInformationDTO.module())
            .bean(new MetaDataFixInconsistenciesTask.AdditionalInformation(
                INSTANT,
                MetaDataFixInconsistenciesService.RunningOptions.withMessageRatePerSecond(36),
                12, 13, 14, 15,
                ImmutableList.of(
                    new MessageInconsistenciesEntry("a", "b"),
                    new MessageInconsistenciesEntry("c", "d")),
                ImmutableList.of(
                    new MessageInconsistenciesEntry("e", "f"),
                    new MessageInconsistenciesEntry("g", "h"))
            ))
            .json(JSON)
            .verify();
    }
}