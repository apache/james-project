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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.RFC2822Headers;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;

public class MailDispatcherTest {
    private FakeMailContext fakeMailContext;
    private MailStorer mailStorer;

    @Before
    public void setUp() throws Exception {
        fakeMailContext = FakeMailContext.defaultContext();
        mailStorer = mock(MailStorer.class);
    }

    @Test
    public void dispatchShouldStoreMail() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(true)
            .build();

        FakeMail mail = FakeMail.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .state("state")
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
            .build();
        testee.dispatch(mail);

        verify(mailStorer).storeMail(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES, mail);
        verify(mailStorer).storeMail(MailAddressFixture.OTHER_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2, mail);
        verifyNoMoreInteractions(mailStorer);
    }

    @Test
    public void dispatchShouldConsumeMailIfSpecified() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(true)
            .build();

        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .state("state")
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
            .build();
        testee.dispatch(mail);

        assertThat(mail.getState()).isEqualTo(Mail.GHOST);
    }

    @Test
    public void dispatchShouldNotConsumeMailIfNotSpecified() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(false)
            .build();

        String state = "state";
        FakeMail mail = FakeMail.builder()
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
            .state(state)
            .build();
        testee.dispatch(mail);

        assertThat(mail.getState()).isEqualTo(state);
    }

    @Test
    public void errorsShouldBeWellHandled() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(true)
            .build();
        doThrow(new MessagingException())
            .when(mailStorer)
            .storeMail(any(MailAddress.class), any(MailAddress.class), any(Mail.class));

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        Multipart multipart = new MimeMultipart();
        MimeBodyPart bodyPart = new MimeBodyPart();
        bodyPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "toto",
                    "text/plain; charset=UTF-8")
            ));
        multipart.addBodyPart(bodyPart);
        mimeMessage.setContent(multipart);
        mimeMessage.saveChanges();

        FakeMail mail = FakeMail.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .mimeMessage(mimeMessage)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .state("state")
            .build();
        testee.dispatch(mail);

        List<FakeMailContext.SentMail> actual = fakeMailContext.getSentMails();
        FakeMailContext.SentMail expected = new FakeMailContext.SentMail.Builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(ImmutableList.of(MailAddressFixture.ANY_AT_JAMES))
            .state(Mail.ERROR).build();
        assertThat(actual).containsOnly(expected);
        assertThat(IOUtils.toString(actual.get(0).getMsg().getInputStream(), Charsets.UTF_8))
            .contains("toto");
    }

    @Test
    public void dispatchShouldUpdateReturnPath() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
            .state("state")
            .build();
        testee.dispatch(mail);

        ArgumentCaptor<Mail> mailCaptor = ArgumentCaptor.forClass(Mail.class);
        verify(mailStorer).storeMail(any(MailAddress.class), any(MailAddress.class), mailCaptor.capture());

        assertThat(mailCaptor.getValue().getMessage().getHeader(RFC2822Headers.RETURN_PATH))
            .containsExactly("<" + MailAddressFixture.OTHER_AT_JAMES +">");
    }

    @Test
    public void dispatchShouldPreserveDeliveredTo() throws Exception {
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(mailStorer)
            .consume(false)
            .build();

        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        String delivered_to_1 = "delivered_to_1";
        String delivered_to_2 = "delivered_to_2";
        mimeMessage.addHeader(MailDispatcher.DELIVERED_TO, delivered_to_1);
        mimeMessage.addHeader(MailDispatcher.DELIVERED_TO, delivered_to_2);
        FakeMail mail = FakeMail.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES)
            .mimeMessage(mimeMessage)
            .state("state")
            .build();
        testee.dispatch(mail);

        assertThat(mimeMessage.getHeader(MailDispatcher.DELIVERED_TO)).containsExactly(delivered_to_1, delivered_to_2);
    }

    @Test
    public void dispatchShouldCustomizeDeliveredToHeader() throws Exception {
        AccumulatorDeliveredToHeaderMailStorer accumulator = new AccumulatorDeliveredToHeaderMailStorer();
        MailDispatcher testee = MailDispatcher.builder()
            .log(mock(Log.class))
            .mailetContext(fakeMailContext)
            .mailStorer(accumulator)
            .consume(false)
            .build();

        FakeMail mail = FakeMail.builder()
            .sender(MailAddressFixture.OTHER_AT_JAMES)
            .recipients(MailAddressFixture.ANY_AT_JAMES, MailAddressFixture.ANY_AT_JAMES2)
            .mimeMessage(new MimeMessage(Session.getDefaultInstance(new Properties())))
            .state("state")
            .build();
        testee.dispatch(mail);

        assertThat(accumulator.getDeliveredToHeaderValues())
            .containsExactly(new String[]{MailAddressFixture.ANY_AT_JAMES.toString()},
                new String[]{MailAddressFixture.ANY_AT_JAMES2.toString()});
    }

    public static class AccumulatorDeliveredToHeaderMailStorer implements MailStorer {
        public final List<String[]> deliveredToHeaderValues;

        public AccumulatorDeliveredToHeaderMailStorer() {
            this.deliveredToHeaderValues = new ArrayList<String[]>();
        }

        @Override
        public void storeMail(MailAddress sender, MailAddress recipient, Mail mail) throws MessagingException {
            deliveredToHeaderValues.add(mail.getMessage().getHeader(MailDispatcher.DELIVERED_TO));
        }

        public List<String[]> getDeliveredToHeaderValues() {
            return deliveredToHeaderValues;
        }
    }
}
