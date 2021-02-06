/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.jsieve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

class DiscardActionTest {
    @Test
    void removeRecipientShouldWorkWhenOnlyOneRecipient() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .build();
        ActionContext actionContext = mock(ActionContext.class);
        when(actionContext.getRecipient()).thenReturn(MailAddressFixture.ANY_AT_JAMES);

        DiscardAction.removeRecipient(mail, actionContext);

        assertThat(mail.getRecipients()).isEmpty();
    }

    @Test
    void removeRecipientShouldNotThrowWhenRecipientIsAbsent() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .build();
        ActionContext actionContext = mock(ActionContext.class);
        when(actionContext.getRecipient()).thenReturn(MailAddressFixture.OTHER_AT_JAMES);

        DiscardAction.removeRecipient(mail, actionContext);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    void removeRecipientShouldNotThrowWhenRecipientIsNull() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .build();
        ActionContext actionContext = mock(ActionContext.class);
        when(actionContext.getRecipient()).thenReturn(null);

        DiscardAction.removeRecipient(mail, actionContext);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES);
    }

    @Test
    void removeRecipientShouldRemoveOnlyTheConcernedRecipient() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();
        ActionContext actionContext = mock(ActionContext.class);
        when(actionContext.getRecipient()).thenReturn(MailAddressFixture.ANY_AT_JAMES);

        DiscardAction.removeRecipient(mail, actionContext);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.OTHER_AT_JAMES);
    }
}
