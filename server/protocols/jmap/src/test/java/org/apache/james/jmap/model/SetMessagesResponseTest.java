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

package org.apache.james.jmap.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.apache.commons.lang.NotImplementedException;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetMessagesResponseTest {

    @Test
    public void builderShouldThrowWhenAccountIdIsGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().accountId(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenOldStateGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().oldState(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldThrowWhenNewStateIsGiven() {
        assertThatThrownBy(() -> SetMessagesResponse.builder().newState(""))
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    public void builderShouldWork() {
        ZonedDateTime currentDate = ZonedDateTime.now();
        ImmutableList<Message> created = ImmutableList.of(
            Message.builder()
                .id(MessageId.of("user|created|1"))
                .blobId("blobId")
                .threadId("threadId")
                .mailboxIds(ImmutableList.of("mailboxId"))
                .headers(ImmutableMap.of("key", "value"))
                .subject("subject")
                .size(123)
                .date(currentDate)
                .preview("preview")
                .build());
        ImmutableList<MessageId> updated = ImmutableList.of(MessageId.of("user|updated|1"));
        ImmutableList<MessageId> destroyed = ImmutableList.of(MessageId.of("user|destroyed|1"));
        ImmutableMap<MessageId, SetError> notCreated = ImmutableMap.of(MessageId.of("user|created|2"), SetError.builder().type("created").build());
        ImmutableMap<MessageId, SetError> notUpdated = ImmutableMap.of(MessageId.of("user|update|2"), SetError.builder().type("updated").build());
        ImmutableMap<MessageId, SetError> notDestroyed  = ImmutableMap.of(MessageId.of("user|destroyed|3"), SetError.builder().type("destroyed").build());
        SetMessagesResponse expected = new SetMessagesResponse(null, null, null, created, updated, destroyed, notCreated, notUpdated, notDestroyed);

        SetMessagesResponse setMessagesResponse = SetMessagesResponse.builder()
            .created(created)
            .updated(updated)
            .destroyed(destroyed)
            .notCreated(notCreated)
            .notUpdated(notUpdated)
            .notDestroyed(notDestroyed)
            .build();

        assertThat(setMessagesResponse).isEqualToComparingFieldByField(expected);
    }
}
