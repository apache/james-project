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
package org.apache.james.mailbox.jpa.mail.model.openjpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import jakarta.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.Test;

class JPAMailboxMessageTest {

    private static final byte[] EMPTY = new byte[]{};

    /**
     * Even though there should never be a null body, it does happen. See JAMES-2384
     */
    @Test
    void getFullContentShouldReturnOriginalContentWhenBodyFieldIsNull() throws Exception {

        // Prepare the message
        byte[] content = "Subject: the null message".getBytes(StandardCharsets.UTF_8);
        JPAMailboxMessage message = new JPAMailboxMessage(content, null);

        // Get and check
        assertThat(IOUtils.toByteArray(message.getFullContent())).containsExactly(content);

    }

    @Test
    void getAnyMessagePartThatIsNullShouldYieldEmptyArray() throws Exception {

        // Prepare the message
        JPAMailboxMessage message = new JPAMailboxMessage(null, null);
        assertThat(IOUtils.toByteArray(message.getHeaderContent())).containsExactly(EMPTY);
        assertThat(IOUtils.toByteArray(message.getBodyContent())).containsExactly(EMPTY);
        assertThat(IOUtils.toByteArray(message.getFullContent())).containsExactly(EMPTY);
    }

    @Test
    void messagesWithTheSameMailboxAndUidShouldBeEqual() throws Exception {
        JPAMailbox mailbox = new JPAMailbox(MailboxPath.inbox(Username.of("bob")), 42);
        byte[] content = "Subject: test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8);
        JPAMailboxMessage message1 = new JPAMailboxMessage(mailbox, new Date(), content.length, new Flags(), new ByteContent(content), 15);
        JPAMailboxMessage message2 = new JPAMailboxMessage(mailbox, new Date(), content.length, new Flags(), new ByteContent(content), 15);
        message1.setUid(MessageUid.of(7));
        message2.setUid(MessageUid.of(7));

        assertThat(message1).isEqualTo(message2);
    }
}
