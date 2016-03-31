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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.jmap.model.MailboxCreationId;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.MailboxRequest;
import org.apache.james.jmap.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.TestId;
import org.junit.Before;
import org.junit.Test;

public class SetMailboxesCreationProcessorTest {

    private MailboxUtils<TestId> mailboxUtils;
    private SetMailboxesCreationProcessor<TestId> sut;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        mailboxUtils = mock(MailboxUtils.class);
        sut = new SetMailboxesCreationProcessor<>(mock(MailboxManager.class), mailboxUtils);
    }

    @Test
    public void processShouldReturnNotCreatedWhenMailboxExceptionOccured() throws Exception {
        String parentId = "parentId";
        MailboxCreationId mailboxCreationId = MailboxCreationId.of("1");
        SetMailboxesRequest request = SetMailboxesRequest.builder()
                .create(mailboxCreationId, MailboxRequest.builder().name("name").parentId(parentId).build())
                .build();

        MailboxSession mailboxSession = mock(MailboxSession.class);
        when(mailboxUtils.getMailboxNameFromId(parentId, mailboxSession))
            .thenThrow(new MailboxException());

        SetMailboxesResponse setMailboxesResponse = sut.process(request, mailboxSession);
        assertThat(setMailboxesResponse.getCreated()).isEmpty();
        assertThat(setMailboxesResponse.getNotCreated()).containsEntry(mailboxCreationId, 
                SetError.builder()
                    .type("anErrorOccurred")
                    .description("An error occurred when creating the mailbox '1'")
                    .build());
    }
}
