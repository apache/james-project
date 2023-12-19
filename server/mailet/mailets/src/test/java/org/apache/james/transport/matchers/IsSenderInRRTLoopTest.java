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

package org.apache.james.transport.matchers;

import static org.apache.mailet.base.MailAddressFixture.JAMES_LOCAL_DOMAIN;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT1;
import static org.apache.mailet.base.MailAddressFixture.RECIPIENT2;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.mock.SimpleDomainList;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.mailet.LoopPrevention;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IsSenderInRRTLoopTest {
    public static final Domain DOMAIN = Domain.of("domain.tld");

    private RecipientRewriteTable recipientRewriteTable;
    private IsSenderInRRTLoop testee;

    @BeforeEach
    void setUp() throws Exception {
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        SimpleDomainList domainList = new SimpleDomainList();
        domainList.addDomain(DOMAIN);
        domainList.addDomain(JAMES_LOCAL_DOMAIN);
        ((MemoryRecipientRewriteTable) recipientRewriteTable).setDomainList(domainList);
        ((MemoryRecipientRewriteTable) recipientRewriteTable).setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        testee = new IsSenderInRRTLoop(recipientRewriteTable);
    }

    @Test
    void matchShouldReturnEmptyWhenSenderHasNoRRT() throws Exception {
        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnRecipientsWhenSenderIsRecorded() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build();
        LoopPrevention.RecordedRecipients.fromMail(mail).merge(SENDER).recordOn(mail);
        Collection<MailAddress> result = testee.match(mail);

        assertThat(result).containsOnly(RECIPIENT1);
    }

    @Test
    void matchShouldReturnEmptyWhenOnlyRecipientIsRecorded() throws Exception {
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build();
        LoopPrevention.RecordedRecipients.fromMail(mail).merge(RECIPIENT1).recordOn(mail);
        Collection<MailAddress> result = testee.match(mail);

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldNotFailWhenNoSender() throws Exception {
        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("name")
            .recipient(RECIPIENT1)
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnEmptyWhenNoRRTLoop() throws Exception {
        recipientRewriteTable.addAddressMapping(MappingSource.fromUser(SENDER.getLocalPart(), SENDER.getDomain()), RECIPIENT1.asString());

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .recipient(RECIPIENT1)
            .build());

        assertThat(result).isEmpty();
    }

    @Test
    void matchShouldReturnRecipientsWhenLoop() throws Exception {
        recipientRewriteTable.addAddressMapping(MappingSource.fromUser(SENDER.getLocalPart(), SENDER.getDomain()),"recipient1@domain.tld");
        recipientRewriteTable.addAddressMapping(MappingSource.fromUser(RECIPIENT1.getLocalPart(), RECIPIENT1.getDomain()), SENDER.asString());
        // required overwise the loop is detected upon insertion
        recipientRewriteTable.addDomainMapping(MappingSource.fromDomain(Domain.of("domain.tld")), Domain.LOCALHOST);

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .recipient(RECIPIENT2)
            .build());

        assertThat(result).containsOnly(RECIPIENT2);
    }

    @Test
    void matchShouldReturnEmptyWhenLoopButNoRecipient() throws Exception {
        recipientRewriteTable.addAddressMapping(MappingSource.fromUser(SENDER.getLocalPart(), SENDER.getDomain()),"recipient1@domain.tld");
        recipientRewriteTable.addAddressMapping(MappingSource.fromUser(RECIPIENT1.getLocalPart(), RECIPIENT1.getDomain()), SENDER.asString());
        // required overwise the loop is detected upon insertion
        recipientRewriteTable.addDomainMapping(MappingSource.fromDomain(DOMAIN), Domain.LOCALHOST);

        Collection<MailAddress> result = testee.match(FakeMail.builder()
            .name("name")
            .sender(SENDER)
            .build());

        assertThat(result).isEmpty();
    }

}