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

package org.apache.mailet.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import javax.activation.DataHandler;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.mailet.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Test;

public class AutomaticallySentMailDetectorImplTest {

    @Test
    public void ownerIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("owner-list@any.com"));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void requestIsAMailingListPrefix() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("list-request@any.com"));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void mailerDaemonIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("MAILER-DAEMON@any.com"));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listservIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("LISTSERV@any.com"));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void majordomoIsReserved() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("majordomo@any.com"));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listIdShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Id", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listHelpShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Help", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listSubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Subscribe", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listUnsubscribeShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Unsubscribe", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listPostShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Post", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listOwnerShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Owner", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void listArchiveShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("List-Archive", "any");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isTrue();
    }

    @Test
    public void normalMailShouldNotBeIdentifiedAsMailingList() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        fakeMail.setMessage(new MimeMessage(Session.getDefaultInstance(new Properties())));

        assertThat(new AutomaticallySentMailDetectorImpl().isMailingList(fakeMail)).isFalse();
    }

    @Test
    public void isAutoSubmittedShouldNotMatchNonAutoSubmittedMails() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setMessage(new MimeMessage(Session.getDefaultInstance(new Properties())));

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isFalse();
    }

    @Test
    public void isAutoSubmittedShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setHeader("Auto-Submitted", "auto-replied");
        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isAutoSubmitted(fakeMail)).isTrue();
    }

    @Test
    public void isMdnSentAutomaticallyShouldBeDetected() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-automatically",
                    "message/disposition-notification;")
            ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isTrue();
    }

    @Test
    public void isMdnSentAutomaticallyShouldNotFilterManuallySentMdn() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-manually",
                    "message/disposition-notification; charset=UTF-8")
            ));
        scriptPart.setHeader("Content-Type", "message/disposition-notification");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

    @Test
    public void isMdnSentAutomaticallyShouldManageItsMimeType() throws Exception {
        FakeMail fakeMail = new FakeMail();
        fakeMail.setSender(new MailAddress("any@any.com"));
        MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart scriptPart = new MimeBodyPart();
        scriptPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    "Disposition: MDN-sent-automatically",
                    "text/plain")
            ));
        scriptPart.setHeader("Content-Type", "text/plain");
        multipart.addBodyPart(scriptPart);
        message.setContent(multipart);

        fakeMail.setMessage(message);

        assertThat(new AutomaticallySentMailDetectorImpl().isMdnSentAutomatically(fakeMail)).isFalse();
    }

}
