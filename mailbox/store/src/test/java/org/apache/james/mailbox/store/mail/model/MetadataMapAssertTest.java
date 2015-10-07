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

import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;
import org.junit.Before;
import org.junit.Test;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MetadataMapAssertTest {

    private static final Long UID = 18L;
    private static final Long MODSEQ = 24L;
    private static final Date DATE = new Date();
    private static final String HEADER_STRING = "name: headerName\n\n";
    private static final String BODY_STRING = "body\\n.\\n";
    private static final TestId MAILBOX_ID = TestId.of(12L);

    private SimpleMessage<TestId> message1;

    @Before
    public void setUp() {
        message1 = new SimpleMessage<TestId>(DATE, HEADER_STRING.length() + BODY_STRING.length(),
            HEADER_STRING.length(), new SharedByteArrayInputStream((HEADER_STRING + BODY_STRING).getBytes()), new Flags(), new PropertyBuilder(), MAILBOX_ID);
        message1.setUid(UID);
        message1.setModSeq(MODSEQ);
    }

    @Test
    public void metadataMapAssertShouldSucceedWhenContainingRightMetadata() {
        Map<Long, MessageMetaData> metaDataMap = new HashMap<Long, MessageMetaData>();
        metaDataMap.put(18L, new SimpleMessageMetaData(UID, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), DATE));
        MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1);
    }

    @Test(expected = AssertionError.class)
    public void metadataMapAssertShouldFailWhenUidMismatch() {
        Map<Long, MessageMetaData> metaDataMap = new HashMap<Long, MessageMetaData>();
        metaDataMap.put(18L, new SimpleMessageMetaData(UID + 1, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), DATE));
        MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1);
    }

    @Test(expected = AssertionError.class)
    public void metadataMapAssertShouldFailWhenDateMismatch() {
        Map<Long, MessageMetaData> metaDataMap = new HashMap<Long, MessageMetaData>();
        Date date = new Date();
        date.setTime(DATE.getTime() + 100L);
        metaDataMap.put(18L, new SimpleMessageMetaData(UID, MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length(), date));
        MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1);
    }

    @Test(expected = AssertionError.class)
    public void metadataMapAssertShouldFailWhenSizeMismatch() {
        Map<Long, MessageMetaData> metaDataMap = new HashMap<Long, MessageMetaData>();
        metaDataMap.put(18L, new SimpleMessageMetaData(UID , MODSEQ, new Flags(), HEADER_STRING.length() + BODY_STRING.length() + 1, DATE));
        MetadataMapAssert.assertThat(metaDataMap).containsMetadataForMessages(message1);
    }


}
