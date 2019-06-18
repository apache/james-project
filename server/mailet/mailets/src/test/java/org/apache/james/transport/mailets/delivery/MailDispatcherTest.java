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

package org.apache.james.transport.mailets.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders.Header;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.collect.ArrayListMultimap;

public class MailDispatcherTest {
    private static final String TEST_HEADER_NAME = "X-HEADER";
    private static final String VALUE_FOR_USER_1 = "value for user 1";
    private static final String VALUE_FOR_USER_2 = "value for user 2";
    private static final Header TEST_HEADER_USER1 = Header.builder().name(TEST_HEADER_NAME).value(VALUE_FOR_USER_1).build();
    private static final Header TEST_HEADER_USER2 = Header.builder().name(TEST_HEADER_NAME).value(VALUE_FOR_USER_2).build();
    
    private FakeMailContext fakeMailContext;
    private MailStore mailStore;

    @Before
    public void setUp() throws Exception {
        fakeMailContext = FakeMailContext.defaultContext();
        mailStore = mock(MailStore.class);
    }

    @Test
    public void dispatchShouldStoreMail() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(mailStore)
            .consume(true)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .state("state")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        testee.dispatch(mail);

        verify(mailStore).storeMail(MailAddressFixture.ANY_AT_JAMES, mail);
        verify(mailStore).storeMail(MailAddressFixture.ANY_AT_JAMES2, mail);
        verifyNoMoreInteractions(mailStore);
    }

    @Test
    public void dispatchShouldConsumeMailIfSpecified() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(mailStore)
            .consume(true)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .state("state")
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .build();
        testee.dispatch(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void dispatchShouldNotConsumeMailIfNotSpecified() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(mailStore)
            .consume(false)
            .build();

        String state = "state";
        FakeMail mail = FakeMail.builder()
            .name("name")
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state(state)
            .build();
        testee.dispatch(mail);

        assertThat(mail.getState()).isEqualTo(state);
    }

    @Test
    public void errorsShouldBeWellHandled() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(mailStore)
            .consume(true)
            .build();
        doThrow(new MessagingException())
            .when(mailStore)
            .storeMail(any(MailAddress.class), any(Mail.class));

        MimeMessageBuilder mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setMultipartWithBodyParts(
                MimeMessageBuilder.bodyPartBuilder()
                    .data("toto"));

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .mimeMessage(mimeMessage)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .state("state")
            .build();
        testee.dispatch(mail);

        List<FakeMailContext.SentMail> actual = fakeMailContext.getSentMails();
        FakeMailContext.SentMail expected = FakeMailContext.sentMailBuilder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipient(MailAddressFixture.ANY_AT_JAMES)
            .fromMailet()
            .state(Mail.ERROR).build();
        assertThat(actual).containsOnly(expected);
        assertThat(IOUtils.toString(actual.get(0).getMsg().getInputStream(), StandardCharsets.UTF_8))
            .contains("toto");
    }

    @Test
    public void dispatchShouldUpdateReturnPath() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(mailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        testee.dispatch(mail);

        ArgumentCaptor<Mail> mailCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(mailStore).storeMail(any(MailAddress.class), mailCaptor.capture());

        assertThat(mailCaptor.getValue().getMessage().getHeader(RFC2822Headers.RETURN_PATH))
            .containsOnly("<" + MailAddressFixture.OTHER_AT_JAMES + ">");
    }

    @Test
    public void dispatchShouldNotAddSpecificHeaderIfRecipientDoesNotMatch() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER2, MailAddressFixture.ANY_AT_JAMES2);
        testee.dispatch(mail);

        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES))
            .isEmpty();
    }

    @Test
    public void dispatchShouldAddSpecificHeaderIfRecipientMatches() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER1, MailAddressFixture.ANY_AT_JAMES);
        testee.dispatch(mail);

        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES))
            .containsOnly(new String[]{VALUE_FOR_USER_1});
    }

    @Test
    public void dispatchShouldNotAddSpecificHeaderToOtherRecipients() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER1, MailAddressFixture.ANY_AT_JAMES);
        testee.dispatch(mail);

        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES))
            .containsOnly(new String[]{VALUE_FOR_USER_1});
        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES2))
            .isEmpty();
    }

    @Test
    public void dispatchShouldAddSpecificHeaderToEachRecipients() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER1, MailAddressFixture.ANY_AT_JAMES);
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER2, MailAddressFixture.ANY_AT_JAMES2);
        testee.dispatch(mail);

        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES))
            .containsOnly(new String[]{VALUE_FOR_USER_1});
        assertThat(accumulatorTestHeaderMailStore.getHeaderValues(MailAddressFixture.ANY_AT_JAMES2))
            .containsOnly(new String[]{VALUE_FOR_USER_2});
    }

    @Test
    public void dispatchShouldNotAlterOriginalMessageWhenPerRecipientHeaderDoesNotExist() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageUtil.defaultMimeMessage())
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER1, MailAddressFixture.ANY_AT_JAMES);
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER2, MailAddressFixture.ANY_AT_JAMES2);
        testee.dispatch(mail);

        assertThat(mail.getMessage().getHeader(TEST_HEADER_NAME)).isNull();
    }

    @Test
    public void dispatchShouldNotAlterOriginalMessageWhenPerRecipientHeaderExists() throws Exception {
        AccumulatorHeaderMailStore accumulatorTestHeaderMailStore = new AccumulatorHeaderMailStore(TEST_HEADER_NAME);
        MailDispatcher testee = MailDispatcher.builder()
            .mailetContext(fakeMailContext)
            .mailStore(accumulatorTestHeaderMailStore)
            .consume(false)
            .build();

        String headerValue = "arbitraryValue";
        FakeMail mail = FakeMail.builder()
            .name("name")
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .addHeader(TEST_HEADER_NAME, headerValue))
            .state("state")
            .build();
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER1, MailAddressFixture.ANY_AT_JAMES);
        mail.addSpecificHeaderForRecipient(TEST_HEADER_USER2, MailAddressFixture.ANY_AT_JAMES2);
        testee.dispatch(mail);

        assertThat(mail.getMessage().getHeader(TEST_HEADER_NAME)).containsOnly(headerValue);
    }

    public static class AccumulatorHeaderMailStore implements MailStore {
        private final ArrayListMultimap<MailAddress, String[]> headerValues;
        private final String headerName;

        public AccumulatorHeaderMailStore(String headerName) {
            this.headerName = headerName;
            this.headerValues = ArrayListMultimap.create();
        }

        @Override
        public void storeMail(MailAddress recipient, Mail mail) throws MessagingException {
            String[] header = mail.getMessage().getHeader(headerName);
            if (header != null) {
                headerValues.put(recipient, header);
            }
        }

        public Collection<String[]> getHeaderValues(MailAddress recipient) {
            return headerValues.get(recipient);
        }
    }
}
