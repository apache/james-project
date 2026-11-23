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

package org.apache.james.mailbox.inmemory.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.core.Username;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperTest;
import org.junit.jupiter.api.Test;

class MemoryMailboxMapperTest extends MailboxMapperTest {

    final AtomicInteger counter = new AtomicInteger();

    @Override
    protected MailboxMapper createMailboxMapper() {
        return new InMemoryMailboxMapper();
    }

    @Override
    protected MailboxId generateId() {
        return InMemoryId.of(counter.incrementAndGet());
    }

    @Test
    void renameShouldKeepTheStoredACL() throws Exception {
        Mailbox mailbox = mailboxMapper.create(MailboxPath.forUser(Username.of("alice"), "shared"), UidValidity.of(1L)).block();
        Mailbox outdatedCopy = mailboxMapper.findMailboxById(mailbox.getMailboxId()).block();
        MailboxACL acl = new MailboxACL().apply(MailboxACL.command().forUser(Username.of("bob")).rights(MailboxACL.Right.Read).asAddition());
        mailboxMapper.setACL(mailbox, acl).block();

        outdatedCopy.setName("renamed");
        mailboxMapper.rename(outdatedCopy).block();

        assertThat(mailboxMapper.findMailboxById(mailbox.getMailboxId()).block().getACL()).isEqualTo(acl);
    }
}
