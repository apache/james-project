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

package org.apache.james.mailbox.store.mail.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetadataMapAssertTest {

    static final MessageUid UID = MessageUid.of(18);
    static final MessageId MESSAGE_ID = new DefaultMessageId();
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);
    static final ModSeq MODSEQ = ModSeq.of(24L);
    static final Date DATE = new Date();
    static final String HEADER_STRING = "name: headerName\n\n";
    static final String BODY_STRING = "body\\n.\\n";
    static final TestId MAILBOX_ID = TestId.of(12L);

    SimpleMailboxMessage message1;

    @BeforeEach
    void setUp() {
        message1 = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, DATE, HEADER_STRING.length() + BODY_STRING.length(),
            HEADER_STRING.length(), new ByteContent((HEADER_STRING + BODY_STRING).getBytes()), new Flags(), new PropertyBuilder().build(), MAILBOX_ID);
        message1.setUid(UID);
        message1.setModSeq(MODSEQ);
    }

    @Test
    void metadataMapAssertShouldSucceedWhenContainingRightMetadata() {
        Map<MessageUid, MessageMetaData> metaDataMap = new HashMap<>();
        metaDataMap.put(UID, new MessageMetaData(UID, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), DATE, Optional.empty(), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
        
        MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1);
    }

    @Test
    void metadataMapAssertShouldFailWhenUidMismatch() {
        Map<MessageUid, MessageMetaData> metaDataMap = new HashMap<>();
        metaDataMap.put(UID, new MessageMetaData(UID.next(), MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), DATE, Optional.empty(), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));
        
        assertThatThrownBy(() -> MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void metadataMapAssertShouldFailWhenDateMismatch() {
        Map<MessageUid, MessageMetaData> metaDataMap = new HashMap<>();
        Date date = new Date();
        date.setTime(DATE.getTime() + 100L);
        metaDataMap.put(UID, new MessageMetaData(UID, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), date, Optional.empty(), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));

        assertThatThrownBy(() -> MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    void metadataMapAssertShouldFailWhenSizeMismatch() {
        Map<MessageUid, MessageMetaData> metaDataMap = new HashMap<>();
        metaDataMap.put(UID, new MessageMetaData(UID, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length() + 1, DATE, Optional.empty(), MESSAGE_ID, ThreadId.fromBaseMessageId(MESSAGE_ID)));

        assertThatThrownBy(() -> MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1))
            .isInstanceOf(AssertionError.class);
    }


}
