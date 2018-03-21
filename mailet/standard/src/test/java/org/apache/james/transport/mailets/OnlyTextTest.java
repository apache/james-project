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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

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
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa è una prova", mail.getMessage().getContent());

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
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questo è un part interno1", mail.getMessage()
                .getContent());

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
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questo è un part interno1", mail.getMessage()
                .getContent());

        // ---------------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        message.setContent("<p>Questa è una prova<br />di html</p>",
                "text/html");
        message.saveChanges();

        mail = FakeMail.builder()
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa è una prova\ndi html\n", mail.getMessage()
                .getContent());
        assertTrue(mail.getMessage().isMimeType("text/plain"));

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
                .mimeMessage(message)
                .build();
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa è una prova\ndi html\n", mail.getMessage()
                .getContent());
        assertTrue(mail.getMessage().isMimeType("text/plain"));
    }

    @Test
    void testHtml2Text() throws MessagingException {
        OnlyText mailet = new OnlyText();
        mailet.init(FakeMailetConfig.builder()
                .mailetName("Test")
                .build());

        String html;
        html = "<b>Prova di html</b><br /><p>Un paragrafo</p><LI>e ci mettiamo anche una lista</LI><br>";
        assertEquals(
                "Prova di html\nUn paragrafo\n\n* e ci mettiamo anche una lista\n",
                mailet.html2Text(html));

        html = "<b>Vediamo invece come andiamo con gli entities</b><br />&egrave;&agrave; &amp;grave;<br>";
        assertEquals(
                "Vediamo invece come andiamo con gli entities\nèà &grave;\n",
                mailet.html2Text(html));
    }

}
