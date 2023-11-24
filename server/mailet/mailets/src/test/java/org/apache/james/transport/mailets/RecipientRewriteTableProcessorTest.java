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

package org.apache.james.transport.mailets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import javax.mail.internet.MimeMessage;

class RecipientRewriteTableProcessorTest {
    private static final String NONEDOMAIN = "nonedomain";
    private static final String INVALID_MAIL_ADDRESS = "server-dev@";

    private FakeMail mail;
    private MimeMessage message;
    private MappingsImpl mappings;
    private FakeMailContext mailetContext;
    private MailAddress nonDomainWithDefaultLocal;
    private MemoryRecipientRewriteTable virtualTableStore;
    private RecipientRewriteTableProcessor processor;
    private MemoryDomainList domainList;

    @BeforeEach
    void setup() throws Exception {
        domainList = new MemoryDomainList();
        domainList.addDomain(Domain.LOCALHOST);
        virtualTableStore = new MemoryRecipientRewriteTable();
        virtualTableStore.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);

        mailetContext = FakeMailContext.defaultContext();
        processor = new RecipientRewriteTableProcessor(virtualTableStore, domainList, mailetContext);
        mail = FakeMail.builder().name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.mimeMessageFromBytes("h: v\r\n".getBytes(UTF_8)))
            .build();
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .build();
        message = MimeMessageUtil.mimeMessageFromBytes("h: v\r\n".getBytes(UTF_8));

        nonDomainWithDefaultLocal = new MailAddress(NONEDOMAIN + "@" + MailAddressFixture.JAMES_LOCAL);
    }

    @Test
    void handleMappingsShouldDoNotCareDefaultDomainWhenMappingsDoesNotContainAnyNoneDomainObject() {
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    void handleMappingsShouldReturnTheMailAddressBelongToLocalServer() {
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(nonDomainWithDefaultLocal);
    }

    @Test
    void handleMappingsShouldReturnTheOnlyMailAddressBelongToLocalServer() throws Exception {
        domainList.addDomain(Domain.of(MailAddressFixture.JAMES2_APACHE_ORG));
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(MailAddressFixture.ANY_AT_LOCAL.toString())
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_LOCAL, new MailAddress("nonedomain@localhost"));
    }

    @Test
    void handleMappingsShouldRemoveMappingElementWhenCannotCreateMailAddress() {
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(nonDomainWithDefaultLocal);
    }

    @Test
    void handleMappingsShouldForwardEmailToRemoteServer() throws Exception {
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Attribute attribute = AttributeName.of("dont-loose-my-attribute").withValue(AttributeValue.of("ok ?"));
        mail.setAttribute(attribute);

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .recipients(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES))
                .fromMailet()
                .attribute(attribute)
                .message(message)
                .build();

        assertThat(mailetContext.getSentMails()).containsOnly(expected);
    }

    @Test
    void handleMappingsShouldNotForwardAnyEmailToRemoteServerWhenNoMoreReomoteAddress() {
        mappings = MappingsImpl.builder()
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .build();

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(mailetContext.getSentMails()).isEmpty();
    }
    
    @Test
    void handleMappingWithOnlyLocalRecipient() {
        mappings = MappingsImpl.builder()
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .add(MailAddressFixture.ANY_AT_LOCAL.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(nonDomainWithDefaultLocal, MailAddressFixture.ANY_AT_LOCAL);
    }
    
    @Test
    void handleMappingWithOnlyRemoteRecipient() throws Exception {
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .recipients(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES))
                .fromMailet()
                .message(message)
                .build();

        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(result).isEmpty();
    }
    
    @Test
    void processShouldNotRewriteRecipientWhenVirtualTableStoreReturnNullMappings() throws Exception {
        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();

        processor.processMail(mail);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }
    
    @Test
    void processShouldSendMailToAllErrorRecipientsWhenErrorMappingException() throws Exception {
        virtualTableStore.addMapping(MappingSource.fromMailAddress(MailAddressFixture.OTHER_AT_LOCAL), Mapping.error("bam"));

        mail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .recipients(MailAddressFixture.OTHER_AT_LOCAL, MailAddressFixture.ANY_AT_LOCAL)
            .build();

        processor.processMail(mail);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .recipient(MailAddressFixture.OTHER_AT_LOCAL)
                .message(message)
                .fromMailet()
                .state(Mail.ERROR)
                .build();

        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_LOCAL);
    }

    @Test
    void processShouldNotDuplicateRewrittenMailAddresses() throws Exception {
        virtualTableStore.addMapping(
            MappingSource.fromMailAddress(MailAddressFixture.OTHER_AT_LOCAL),
            Mapping.alias(MailAddressFixture.ANY_AT_LOCAL.asString()));
        virtualTableStore.addMapping(
            MappingSource.fromMailAddress(MailAddressFixture.OTHER_AT_LOCAL),
            Mapping.group(MailAddressFixture.ANY_AT_LOCAL.asString()));

        mail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .recipients(MailAddressFixture.OTHER_AT_LOCAL)
            .build();

        processor.processMail(mail);

        assertThat(mail.getRecipients()).containsExactly(MailAddressFixture.ANY_AT_LOCAL);
    }
    
    @Test
    void processShouldSendMailToAllErrorRecipientsWhenRecipientRewriteTableException() throws Exception {
        virtualTableStore.addMapping(MappingSource.fromMailAddress(MailAddressFixture.OTHER_AT_LOCAL), Mapping.error("bam"));

        mail = FakeMail.builder()
            .name("mail")
            .sender(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(message)
            .recipients(MailAddressFixture.OTHER_AT_LOCAL, MailAddressFixture.ANY_AT_LOCAL)
            .build();

        processor.processMail(mail);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .recipient(MailAddressFixture.OTHER_AT_LOCAL)
                .message(message)
                .state(Mail.ERROR)
                .fromMailet()
                .build();

        assertThat(mailetContext.getSentMails()).containsOnly(expected);
        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_LOCAL);
    }
    
    @Test
    void processShouldNotSendMailWhenNoErrorRecipients() throws Exception {
        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.ANY_AT_JAMES, nonDomainWithDefaultLocal)
            .build();

        processor.processMail(mail);

        assertThat(mailetContext.getSentMails()).isEmpty();
    }
    
    @Test
    void processShouldResetMailStateToGhostWhenCanNotBuildNewRecipient() throws Exception {
        virtualTableStore.addMapping(
            MappingSource.fromDomain(MailAddressFixture.JAMES_APACHE_ORG_DOMAIN),
            Mapping.of(MailAddressFixture.ANY_AT_JAMES2.asString()));

        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES)
            .build();

        processor.processMail(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
        assertThat(mail.getRecipients()).isEmpty();
    }

}