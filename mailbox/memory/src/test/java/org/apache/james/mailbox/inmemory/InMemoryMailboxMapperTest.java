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

package org.apache.james.mailbox.inmemory;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

public class InMemoryMailboxMapperTest {

    public static final int UID_VALIDITY = 10;

    private MailboxPath user1InboxPath;
    private MailboxPath user1SubMailbox1Path;
    private MailboxPath user1SubMailbox2Path;
    private MailboxPath user2OtherBoxPath;
    private MailboxPath user1OtherNamespacePath;

    private Mailbox<InMemoryId> user1Inbox;
    private Mailbox<InMemoryId> user1SubMailbox1;
    private Mailbox<InMemoryId> user1SubMailbox2;

    private MailboxMapper<InMemoryId> mapper;

    @Before
    public void setUp() throws MailboxException {
        user1InboxPath = new MailboxPath("#private", "user1", "INBOX");
        user1SubMailbox1Path = new MailboxPath("#private", "user1", "INBOX.sub1");
        user1SubMailbox2Path = new MailboxPath("#private", "user1", "INBOX.sub2");
        user2OtherBoxPath = new MailboxPath("#private", "user2", "other.user");
        user1OtherNamespacePath = new MailboxPath("#namspace", "user1", "other.namespace");
        user1Inbox = new SimpleMailbox<InMemoryId>(user1InboxPath, UID_VALIDITY);
        user1SubMailbox1 = new SimpleMailbox<InMemoryId>(user1SubMailbox1Path, UID_VALIDITY);
        user1SubMailbox2 = new SimpleMailbox<InMemoryId>(user1SubMailbox2Path, UID_VALIDITY);
        mapper = new InMemoryMailboxSessionMapperFactory().createMailboxMapper(new MockMailboxSession("user"));
        mapper.save(user1Inbox);
        mapper.save(user1SubMailbox1);
        mapper.save(user1SubMailbox2);
        mapper.save(new SimpleMailbox<InMemoryId>(user2OtherBoxPath, UID_VALIDITY));
        mapper.save(new SimpleMailbox<InMemoryId>(user1OtherNamespacePath, UID_VALIDITY));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findMailboxWithPatchLikeOnAllMaillboxesShouldReturnMailboxesBelongingToThisNamespaceAndUser() throws MailboxException{
        assertThat(mapper.findMailboxWithPathLike(new MailboxPath("#private", "user1", "%")))
            .containsOnly(user1Inbox, user1SubMailbox1, user1SubMailbox2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findMailboxWithPatchLikeBasedOnInboxShouldReturnItsChildren() throws MailboxException{
        assertThat(mapper.findMailboxWithPathLike(new MailboxPath("#private", "user1", "INBOX.%")))
            .containsOnly(user1SubMailbox1, user1SubMailbox2);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void findMailboxWithPatchLikeBasedOnAStringShouldReturnMailboxesStartingWithThisString() throws MailboxException{
        assertThat(mapper.findMailboxWithPathLike(new MailboxPath("#private", "user1", "IN%")))
            .containsOnly(user1Inbox, user1SubMailbox1, user1SubMailbox2);
    }

}

