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

import org.apache.james.transport.mailets.OnlyText;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;

public class OnlyTextTest extends TestCase {

    /**
     * Test method for
     * 'it.voidlabs.elysium.mailgateway.transport.mailets.OnlyText.service(Mail)
     * '
     * 
     * @throws MessagingException
     * @throws IOException
     */
    public void testService() throws MessagingException, IOException {
        Mailet mailet;
        FakeMailetConfig mci;
        MimeMessage message;
        Mail mail;

        mailet = new OnlyText();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mailet.init(mci);

        // ----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        message.setText("Questa \u00E8 una prova");
        message.saveChanges();

        mail = new FakeMail(message);
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa \u00E8 una prova", mail.getMessage().getContent());

        // -----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        MimeMultipart mp = new MimeMultipart();
        MimeBodyPart bp = new MimeBodyPart();
        bp.setText("Questo \u00E8 un part interno1");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        bp.setText("Questo \u00E8 un part interno2");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        MimeMessage message2 = new MimeMessage(Session
                .getDefaultInstance(new Properties()));
        bp.setContent(message2, "message/rfc822");
        mp.addBodyPart(bp);
        message.setContent(mp);
        message.saveChanges();

        mail = new FakeMail(message);
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questo \u00E8 un part interno1", mail.getMessage()
                .getContent());

        // -----------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        mp = new MimeMultipart();
        bp = new MimeBodyPart();
        bp.setText("Questo \u00E8 un part interno1");
        mp.addBodyPart(bp);
        bp = new MimeBodyPart();
        bp.setText("Questo \u00E8 un part interno2");
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

        mail = new FakeMail(message);
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questo \u00E8 un part interno1", mail.getMessage()
                .getContent());

        // ---------------------

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("prova");
        message.setContent("<p>Questa \u00E8 una prova<br />di html</p>",
                "text/html");
        message.saveChanges();

        mail = new FakeMail(message);
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa \u00E8 una prova\ndi html\n", mail.getMessage()
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
        bp.setContent("<p>Questa \u00E8 una prova<br />di html</p>", "text/html");
        mp.addBodyPart(bp);
        message.setContent(mp);
        message.saveChanges();

        mail = new FakeMail(message);
        mailet.service(mail);

        assertEquals("prova", mail.getMessage().getSubject());
        assertEquals("Questa \u00E8 una prova\ndi html\n", mail.getMessage()
                .getContent());
        assertTrue(mail.getMessage().isMimeType("text/plain"));
    }

    public void testHtml2Text() throws MessagingException {
        OnlyText mailet = new OnlyText();
        mailet.init(new FakeMailetConfig("Test", new FakeMailContext()));

        String html;
        html = "<b>Prova di html</b><br /><p>Un paragrafo</p><LI>e ci mettiamo anche una lista</LI><br>";
        assertEquals(
                "Prova di html\nUn paragrafo\n\n* e ci mettiamo anche una lista\n",
                mailet.html2Text(html));

        html = "<b>Vediamo invece come andiamo con gli entities</b><br />&egrave;&agrave; &amp;grave;<br>";
        assertEquals(
                "Vediamo invece come andiamo con gli entities\n\u00E8\u00E0 &grave;\n",
                mailet.html2Text(html));
    }

}
