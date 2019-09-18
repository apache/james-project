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

package org.apache.james.jmap.draft.model.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MailboxId;
import org.junit.Test;

public class MailboxUpdateRequestTest {
    
    @Test
    public void getParentIdShouldReturnEmptyWhenNotGiven() throws Exception {
        //Given
        MailboxUpdateRequest testee = MailboxUpdateRequest.builder().name("my box").build();
        //When
        Optional<MailboxId> actual = testee.getParentId();
        //Then
        assertThat(actual).isEmpty();
    }

    @Test
    public void getParentIdShouldReturnNullWhenNullParentIdGiven() throws Exception {
        //Given
        MailboxUpdateRequest testee = MailboxUpdateRequest.builder().name("my box").parentId(null).build();
        //When
        Optional<MailboxId> actual = testee.getParentId();
        //Then
        assertThat(actual).isNull();
    }

    @Test
    public void getParentIdShouldReturnParentIdWhenParentIdGiven() throws Exception {
        //Given
        InMemoryId expected = InMemoryId.of(123);
        MailboxUpdateRequest testee = MailboxUpdateRequest.builder().parentId(expected).name("my box").build();
        //When
        Optional<MailboxId> actual = testee.getParentId();
        //Then
        assertThat(actual).contains(expected);
    }
}
