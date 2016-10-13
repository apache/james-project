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
package org.apache.james.mailbox.cassandra.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.assertj.core.data.MapEntry;
import org.junit.Before;
import org.junit.Test;

import com.datastax.driver.core.Session;
import com.google.common.collect.ImmutableList;

public class CassandraMessageMapperTest {

    private AttachmentMapper attachmentMapper;
    private CassandraMessageMapper testee;

    @Before
    public void setup() {
        Session session = mock(Session.class);
        UidProvider uidProvider = mock(UidProvider.class);
        ModSeqProvider modSeqProvider = mock(ModSeqProvider.class);
        MailboxSession mailboxSession = mock(MailboxSession.class);
        int maxRetires = 3;
        attachmentMapper = mock(AttachmentMapper.class);
        CassandraMessageDAO messageDAO = mock(CassandraMessageDAO.class);
        CassandraMessageIdDAO messageIdDAO = mock(CassandraMessageIdDAO.class);
        CassandraMessageIdToImapUidDAO imapUidDAO = mock(CassandraMessageIdToImapUidDAO.class);
        testee = new CassandraMessageMapper(session, uidProvider, modSeqProvider, mailboxSession, maxRetires, attachmentMapper, messageDAO, messageIdDAO, imapUidDAO);
    }

    @Test
    public void attachmentsByIdShouldRemoveDuplicateKeys() {
        AttachmentId attachmentId = AttachmentId.from("1");
        AttachmentId attachmentId2 = AttachmentId.from("2");
        List<AttachmentId> attachmentIds = ImmutableList.of(attachmentId, attachmentId, attachmentId2);

        Attachment attachment = Attachment.builder()
                .attachmentId(attachmentId)
                .bytes("attachment".getBytes())
                .type("type")
                .build();
        Attachment attachment2 = Attachment.builder()
                .attachmentId(attachmentId2)
                .bytes("attachment2".getBytes())
                .type("type")
                .build();
        when(attachmentMapper.getAttachments(attachmentIds))
            .thenReturn(ImmutableList.of(attachment, attachment, attachment2));

        Map<AttachmentId, Attachment> attachmentsById = testee.attachmentsById(attachmentIds);

        assertThat(attachmentsById).containsOnly(MapEntry.entry(attachmentId, attachment),
                MapEntry.entry(attachmentId2, attachment2));
    }
}
