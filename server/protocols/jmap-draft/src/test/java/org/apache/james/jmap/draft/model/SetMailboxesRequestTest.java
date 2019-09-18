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
package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.draft.model.mailbox.MailboxCreateRequest;
import org.apache.james.jmap.draft.model.mailbox.MailboxUpdateRequest;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMailboxesRequestTest {

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAccountId() {
        SetMailboxesRequest.builder().accountId("1");
    }

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenIfInState() {
        SetMailboxesRequest.builder().ifInState("1");
    }

    @Test
    public void builderShouldWork() throws MailboxException {
        //Given
        MailboxCreationId creationId = MailboxCreationId.of("creationId");
        InMemoryId mailboxId = InMemoryId.of(123);
        MailboxCreateRequest mailboxRequest = MailboxCreateRequest.builder()
            .name("mailboxRequest")
            .build();
        ImmutableList<MailboxId> destroy = ImmutableList.of(InMemoryId.of(456));
        MailboxUpdateRequest mailboxUpdateRequest = MailboxUpdateRequest.builder()
            .name("mailboxUpdateRequest")
            .build();
        SetMailboxesRequest expected = new SetMailboxesRequest(ImmutableMap.of(creationId, mailboxRequest), ImmutableMap.of(mailboxId, mailboxUpdateRequest), destroy);

        //When
        SetMailboxesRequest actual = SetMailboxesRequest.builder()
            .create(creationId, mailboxRequest)
            .update(mailboxId, mailboxUpdateRequest)
            .destroy(destroy)
            .build();

        //Then
        assertThat(actual).isEqualToComparingFieldByField(expected);
    }
}
