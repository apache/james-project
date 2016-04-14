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

import java.util.Optional;

import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMailboxesRequest;
import org.apache.james.jmap.model.SetMailboxesResponse;
import org.apache.james.jmap.model.mailbox.Mailbox;
import org.apache.james.jmap.model.mailbox.MailboxUpdateRequest;
import org.apache.james.jmap.utils.MailboxUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.TestId;
import org.junit.Before;
import org.junit.Test;

public class SetMailboxesUpdateProcessorTest {

    private MailboxManager mockedMailboxManager;
    private MailboxUtils<TestId> mockedMailboxUtils;
    private MailboxSession mockedMailboxSession;
    private SetMailboxesUpdateProcessor<TestId> sut;

    @Before
    @SuppressWarnings("unchecked")
    public void setup() {
        mockedMailboxManager = mock(MailboxManager.class);
        mockedMailboxUtils = mock(MailboxUtils.class);
        mockedMailboxSession = mock(MailboxSession.class);
        sut = new SetMailboxesUpdateProcessor<>(mockedMailboxUtils, mockedMailboxManager);
    }

    @Test
    public void processShouldReturnNotUpdatedWhenMailboxExceptionOccured() throws Exception {
        // Given
        String mailboxId = "1";
        String newParentId = "newParentId";
        MailboxPath newParentMailboxPath = new MailboxPath("#private", "user", "newParentName");
        SetMailboxesRequest request = SetMailboxesRequest.builder()
                .update(mailboxId, MailboxUpdateRequest.builder().parentId(newParentId).build())
                .build();
        Mailbox mailbox = Mailbox.builder().id(mailboxId).name("name").role(Optional.empty()).build();
        when(mockedMailboxUtils.mailboxFromMailboxId(mailboxId, mockedMailboxSession))
            .thenReturn(Optional.of(mailbox));
        when(mockedMailboxUtils.mailboxPathFromMailboxId(newParentId, mockedMailboxSession))
            .thenReturn(Optional.of(newParentMailboxPath));
        when(mockedMailboxUtils.hasChildren(mailboxId, mockedMailboxSession))
            .thenThrow(new MailboxException());

        // When
        SetMailboxesResponse setMailboxesResponse = sut.process(request, mockedMailboxSession);

        // Then
        assertThat(setMailboxesResponse.getUpdated()).isEmpty();
        assertThat(setMailboxesResponse.getNotUpdated()).containsEntry(mailboxId, SetError.builder().type("anErrorOccurred").description("An error occurred when updating the mailbox").build());
    }
}
