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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collection;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.rrt.api.RecipientRewriteTable.ErrorMappingException;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingsImpl;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

public class RecipientRewriteTableProcessorTest {
    private static final String NONEDOMAIN = "nonedomain";
    private static final String INVALID_MAIL_ADDRESS = "server-dev@";

    private FakeMail mail;
    private MimeMessage message;
    private MappingsImpl mappings;
    private FakeMailContext mailetContext;
    private MailAddress nonDomainWithDefaultLocal;

    @Mock DomainList domainList;
    @Mock org.apache.james.rrt.api.RecipientRewriteTable virtualTableStore;

    private RecipientRewriteTableProcessor processor;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);
        mailetContext = FakeMailContext.defaultContext();
        processor = new RecipientRewriteTableProcessor(virtualTableStore, domainList, mailetContext);
        mail = FakeMail.builder().name("mail").sender(MailAddressFixture.ANY_AT_JAMES).build();
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .build();
        message = MimeMessageUtil.defaultMimeMessage();

        nonDomainWithDefaultLocal = new MailAddress(NONEDOMAIN + "@" + MailAddressFixture.JAMES_LOCAL);
    }

    @Test(expected = MessagingException.class)
    public void handleMappingsShouldThrowExceptionWhenMappingsContainAtLeastOneNoneDomainObjectButCannotGetDefaultDomain() throws Exception {
        when(domainList.getDefaultDomain()).thenThrow(DomainListException.class);
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        processor.handleMappings(mappings, FakeMail.builder().name("mail").sender(MailAddressFixture.ANY_AT_JAMES).build(), MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    public void handleMappingsShouldDoNotCareDefaultDomainWhenMappingsDoesNotContainAnyNoneDomainObject() throws Exception {
        when(domainList.getDefaultDomain()).thenThrow(DomainListException.class);
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);
    }

    @Test
    public void handleMappingsShouldReturnTheMailAddressBelongToLocalServer() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));
        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(nonDomainWithDefaultLocal);
    }

    @Test
    public void handleMappingsShouldReturnTheOnlyMailAddressBelongToLocalServer() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES2_APACHE_ORG));

        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(MailAddressFixture.ANY_AT_LOCAL.toString())
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(MailAddressFixture.ANY_AT_LOCAL);
    }

    @Test
    public void handleMappingsShouldRemoveMappingElementWhenCannotCreateMailAddress() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));
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
    public void handleMappingsShouldForwardEmailToRemoteServer() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));

        mappings = MappingsImpl.builder()
                .add(MailAddressFixture.ANY_AT_JAMES.toString())
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .add(MailAddressFixture.OTHER_AT_JAMES.toString())
                .build();

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
                .sender(MailAddressFixture.ANY_AT_JAMES)
                .recipients(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES))
                .fromMailet()
                .message(message)
                .build();

        assertThat(mailetContext.getSentMails()).containsOnly(expected);
    }

    @Test
    public void handleMappingsShouldNotForwardAnyEmailToRemoteServerWhenNoMoreReomoteAddress() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));

        mappings = MappingsImpl.builder()
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .build();

        processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(mailetContext.getSentMails()).isEmpty();
    }
    
    @Test
    public void handleMappingWithOnlyLocalRecipient() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));

        mappings = MappingsImpl.builder()
                .add(NONEDOMAIN)
                .add(INVALID_MAIL_ADDRESS)
                .add(MailAddressFixture.ANY_AT_LOCAL.toString())
                .build();

        Collection<MailAddress> result = processor.handleMappings(mappings, mail, MailAddressFixture.OTHER_AT_JAMES);

        assertThat(result).containsOnly(nonDomainWithDefaultLocal, MailAddressFixture.ANY_AT_LOCAL);
    }
    
    @Test
    public void handleMappingWithOnlyRemoteRecipient() throws Exception {
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));

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
    public void processShouldNotRewriteRecipientWhenVirtualTableStoreReturnNullMappings() throws Exception {
        when(virtualTableStore.getResolvedMappings(any(String.class), any(Domain.class))).thenReturn(null);

        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES)
            .build();

        processor.processMail(mail);

        assertThat(mail.getRecipients()).containsOnly(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.OTHER_AT_JAMES);
    }
    
    @Test
    public void processShouldSendMailToAllErrorRecipientsWhenErrorMappingException() throws Exception {
        when(virtualTableStore.getResolvedMappings(eq("other"), eq(Domain.of(MailAddressFixture.JAMES_LOCAL)))).thenThrow(ErrorMappingException.class);

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
    public void processShouldNotDuplicateRewrittenMailAddresses() throws Exception {
        when(virtualTableStore.getResolvedMappings(eq("other"), eq(Domain.of(MailAddressFixture.JAMES_LOCAL))))
            .thenReturn(MappingsImpl.builder()
                .add(Mapping.alias(MailAddressFixture.ANY_AT_LOCAL.asString()))
                .add(Mapping.group(MailAddressFixture.ANY_AT_LOCAL.asString()))
                .build());

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
    public void processShouldSendMailToAllErrorRecipientsWhenRecipientRewriteTableException() throws Exception {
        when(virtualTableStore.getResolvedMappings(eq("other"), eq(Domain.of(MailAddressFixture.JAMES_LOCAL)))).thenThrow(RecipientRewriteTableException.class);

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
    public void processShouldNotSendMailWhenNoErrorRecipients() throws Exception {
        when(virtualTableStore.getResolvedMappings(any(String.class), any(Domain.class))).thenReturn(null);

        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.ANY_AT_JAMES, nonDomainWithDefaultLocal)
            .build();

        processor.processMail(mail);

        assertThat(mailetContext.getSentMails()).isEmpty();
    }
    
    @Test
    public void processShouldResetMailStateToGhostWhenCanNotBuildNewRecipient() throws Exception {
        when(virtualTableStore.getResolvedMappings(any(String.class), any(Domain.class))).thenReturn(mappings);
        when(domainList.getDefaultDomain()).thenReturn(Domain.of(MailAddressFixture.JAMES_LOCAL));

        mail = FakeMail.builder()
            .name("mail")
            .mimeMessage(message)
            .recipients(MailAddressFixture.OTHER_AT_JAMES, nonDomainWithDefaultLocal)
            .build();

        processor.processMail(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
        assertThat(mail.getRecipients()).isEmpty();
    }

}