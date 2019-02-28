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

package org.apache.james.vault;

import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE;
import static org.apache.james.vault.DeletedMessageFixture.DELETED_MESSAGE_WITH_SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.DELETION_DATE;
import static org.apache.james.vault.DeletedMessageFixture.DELIVERY_DATE;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_1;
import static org.apache.james.vault.DeletedMessageFixture.MAILBOX_ID_2;
import static org.apache.james.vault.DeletedMessageFixture.MESSAGE_ID;
import static org.apache.james.vault.DeletedMessageFixture.SUBJECT;
import static org.apache.james.vault.DeletedMessageFixture.USER;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MaybeSender;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class DeletedMessageTest {
    @Test
    void deletedMessageShouldMatchBeanContract() {
        EqualsVerifier.forClass(DeletedMessage.class)
            .verify();
    }

    @Test
    void buildShouldReturnDeletedMessageWithAllCompulsoryFields() {
        SoftAssertions.assertSoftly(
            soft -> {
                soft.assertThat(DELETED_MESSAGE.getMessageId()).isEqualTo(MESSAGE_ID);
                soft.assertThat(DELETED_MESSAGE.getOriginMailboxes()).containsOnly(MAILBOX_ID_1, MAILBOX_ID_2);
                soft.assertThat(DELETED_MESSAGE.getOwner()).isEqualTo(USER);
                soft.assertThat(DELETED_MESSAGE.getDeliveryDate()).isEqualTo(DELIVERY_DATE);
                soft.assertThat(DELETED_MESSAGE.getDeletionDate()).isEqualTo(DELETION_DATE);
                soft.assertThat(DELETED_MESSAGE.getSender()).isEqualTo(MaybeSender.of(SENDER));
                soft.assertThat(DELETED_MESSAGE.getRecipients()).containsOnly(RECIPIENT1, RECIPIENT2);
                soft.assertThat(DELETED_MESSAGE.hasAttachment()).isFalse();
                soft.assertThat(DELETED_MESSAGE.getSubject()).isEmpty();
            }
        );
    }

    @Test
    void buildShouldReturnDeletedMessageWithSubject() {
        assertThat(DELETED_MESSAGE_WITH_SUBJECT.getSubject()).contains(SUBJECT);
    }
}