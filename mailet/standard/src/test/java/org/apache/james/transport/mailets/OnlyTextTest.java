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

import java.io.IOException;
import java.util.Properties;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.jupiter.api.Test;

class OnlyTextTest {

    /**
     * Test method for
     * 'it.voidlabs.elysium.mailgateway.transport.mailets.OnlyText.service(Mail)
     * '
     * 
     * @throws MessagingException
     * @throws IOException
     */
    @Test
    void testService() throws MessagingException, IOException {
        Mailet mailet;
        FakeMailetConfig mci;
        MimeMessage message;
        Mail mail;

        mailet = new OnlyText();
        mci = FakeMailetConfig.builder()
                .mailetName("Test")
                .build();
        mailet.init(mci);

        // ----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        message.setText("Questa è una prova");
        message.saveChanges();

        mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("prova");
        assertThat(mail.getMessage().getContent()).isEqualTo("Questa è una prova");

        // -----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart bp = new MimeBodyPart();
        bp.setText("Questo è un part interno1");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        bp.setText("Questo è un part interno2");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        MimeMessage message2 = new MimeMessage(Session
                .getDefaultInstance(new Properties()));
        bp.setContent(message2, "message/rfc822");
        mp.addBodyPart(bp);
        message.setContent(mp);
        message.saveChanges();

        mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("prova");
        assertThat(mail.getMessage()
                .getContent()).isEqualTo("Questo è un part interno1");

        // -----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        mp = new MimeMultipart();
        bp = new MimeBodyPart();
        bp.setText("Questo è un part interno1");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        bp.setText("Questo è un part interno2");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        message2 = new MimeMessage(Session.getDefaultInstance(new Properties()));
        bp.setContent(message2, "message/rfc822");
        mp.addBodyPart(bp);

        MimeMultipart mpext = new MimeMultipart();
        bp = new MimeBodyPart();
        bp.setContent(mp);
        mpext.addBodyPart(bp);

        message.setContent(mpext);
        message.saveChanges();

        mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("prova");
        assertThat(mail.getMessage()
                .getContent()).isEqualTo("Questo è un part interno1");

        // ---------------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        message.setContent("<p>Questa è una prova<br />di html</p>",
                "text/html");
        message.saveChanges();

        mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("prova");
        assertThat(mail.getMessage()
                .getContent()).isEqualTo("Questa è una prova\ndi html\n");
        assertThat(mail.getMessage().isMimeType("text/plain")).isTrue();

        // -----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        mp = new MimeMultipart();
        bp = new MimeBodyPart();
        message2 = new MimeMessage(Session.getDefaultInstance(new Properties()));
        bp.setContent(message2, "message/rfc822");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        bp.setContent("<p>Questa è una prova<br />di html</p>", "text/html");
        mp.addBodyPart(bp);
        message.setContent(mp);
        message.saveChanges();

        mail = FakeMail.builder()
                .name("mail")
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertThat(mail.getMessage().getSubject()).isEqualTo("prova");
        assertThat(mail.getMessage()
                .getContent()).isEqualTo("Questa è una prova\ndi html\n");
        assertThat(mail.getMessage().isMimeType("text/plain")).isTrue();
    }

    @Test
    void testHtml2Text() throws MessagingException {
        OnlyText mailet = new OnlyText();
        mailet.init(FakeMailetConfig.builder()
                .mailetName("Test")
                .build());

        String html;
        html = "<b>Prova di html</b><br /><p>Un paragrafo</p><LI>e ci mettiamo anche una lista</LI><br>";
        assertThat(mailet.html2Text(html)).isEqualTo("Prova di html\nUn paragrafo\n\n* e ci mettiamo anche una lista\n");

        html = "<b>Vediamo invece come andiamo con gli entities</b><br />&egrave;&agrave; &amp;grave;<br>";
        assertThat(mailet.html2Text(html)).isEqualTo("Vediamo invece come andiamo con gli entities\nèà &grave;\n");
    }

}
