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

import org.apache.commons.lang.NotImplementedException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class GetMailboxesRequestTest {

    @Test(expected = NotImplementedException.class)
    public void builderShouldThrowWhenAccountId() {
        GetMailboxesRequest.builder().accountId("1");
    }

    @Test
    public void builderShouldNotThrowWhenIds() {
        GetMailboxesRequest.builder().ids(ImmutableList.of());
    }

    @Test
    public void idsShouldBeEmptyListWhenEmptyList() {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
            .ids(ImmutableList.of())
            .build();
        assertThat(getMailboxesRequest.getIds()).isPresent();
        assertThat(getMailboxesRequest.getIds().get()).hasSize(0);
    }

    @Test
    public void idsShouldBePresentWhenListIsNotEmpty() {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
            .ids(ImmutableList.of(InMemoryId.of(123)))
            .build();
        assertThat(getMailboxesRequest.getIds()).isPresent();
        assertThat(getMailboxesRequest.getIds().get()).containsExactly(InMemoryId.of(123));
    }

    @Test
    public void propertiesShouldBeEmptyWhenNotGiven() {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder().build();
        assertThat(getMailboxesRequest.getProperties()).isEmpty();
    }

    @Test
    public void propertiesShouldNotBeEmptyWhenEmptyListGiven() {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .properties(ImmutableList.of())
                .build();

        assertThat(getMailboxesRequest.getProperties()).isPresent();
        assertThat(getMailboxesRequest.getProperties().get()).isEmpty();;
    }

    @Test
    public void propertiesShouldNotBeEmptyWhenListGiven() {
        GetMailboxesRequest getMailboxesRequest = GetMailboxesRequest.builder()
                .properties(ImmutableList.of("id"))
                .build();

        assertThat(getMailboxesRequest.getProperties()).isPresent();
        assertThat(getMailboxesRequest.getProperties().get()).containsOnly(MailboxProperty.ID);
    }
}
