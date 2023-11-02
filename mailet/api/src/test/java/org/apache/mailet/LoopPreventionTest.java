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

package org.apache.mailet;

import static org.apache.mailet.LoopPrevention.RECORDED_RECIPIENTS_ATTRIBUTE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public class LoopPreventionTest {

    public static final String DEFAULT_DOMAIN = "james.org";
    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);

    @Test
    void nonRecordedRecipientsShouldWork() throws Exception {
        assertThat(new LoopPrevention.RecordedRecipients(BOB.asMailAddress())
            .nonRecordedRecipients(ALICE.asMailAddress(), BOB.asMailAddress()))
            .containsOnly(ALICE.asMailAddress());
    }

    @Test
    void recordedRecipientsShouldWork() throws Exception {
        Mail mail = mock(Mail.class);

        new LoopPrevention.RecordedRecipients(ALICE.asMailAddress(), BOB.asMailAddress())
            .merge(CEDRIC.asMailAddress())
            .recordOn(mail);

        Attribute attribute = new Attribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME,
            AttributeValue.of(ImmutableList.of(AttributeValue.of(ALICE.asString()),
                AttributeValue.of(BOB.asString()),
                AttributeValue.of(CEDRIC.asString()))));

        verify(mail).setAttribute(attribute);
    }

    @Test
    void fromMailShouldRetrieveRecordedRecipients() throws Exception {
        Mail mail = mock(Mail.class);

        Attribute attribute = new Attribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME,
            AttributeValue.of(ImmutableList.of(AttributeValue.of(ALICE.asString()))));

        when(mail.getAttribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME))
            .thenReturn(Optional.of(attribute));

        assertThat(LoopPrevention.RecordedRecipients.fromMail(mail).getRecipients())
            .containsOnly(ALICE.asMailAddress());
    }

    @Test
    void retrieveRecordedRecipientsShouldReturnEmptyWhenTheAttributeDoesNotExist() {
        Mail mail =  mock(Mail.class);

        when(mail.getAttribute(RECORDED_RECIPIENTS_ATTRIBUTE_NAME))
            .thenReturn(Optional.empty());

        assertThat(LoopPrevention.RecordedRecipients.fromMail(mail).getRecipients())
            .isEmpty();
    }

}
