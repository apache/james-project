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

package org.apache.james.mailbox.jpa.mail;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.jpa.mail.model.JPAAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.AttachmentMapper;
import org.apache.james.mailbox.store.mail.model.AttachmentMapperTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class JPAAttachmentMapperTest extends AttachmentMapperTest {

    public static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    AttachmentMapper attachmentMapper;

    private final AtomicInteger counter = new AtomicInteger();

    @Override
    @Before
    public void setUp() throws MailboxException {
        super.setUp();
        this.attachmentMapper = createAttachmentMapper();
    }

    @After
    public void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Override
    protected AttachmentMapper createAttachmentMapper() {
        return new TransactionalAttachmentMapper(new JPAAttachmentMapper(JPA_TEST_CLUSTER.getEntityManagerFactory()));
    }

    @Override
    protected MessageId generateMessageId() {
        return JPAMessageId.of(counter.incrementAndGet());
    }

    @Test
    public void equalsContractForJPAAttachment() {
        EqualsVerifier.forClass(JPAAttachment.class).verify();
    }


}
