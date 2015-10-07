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

import org.apache.james.transport.mailets.ReplaceContent;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Assert;
import org.junit.Test;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Properties;

public class ReplaceContentTest {

    @Test
    public void testService() throws MessagingException, IOException {
        Mailet mailet;
        FakeMailetConfig mci;
        MimeMessage message;
        Mail mail;

        mailet = new ReplaceContent();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mci.setProperty("subjectPattern", "/prova/PROVA/i/,/a/e//,/o/o/i/");
        mci.setProperty("bodyPattern", "/prova/PROVA/i/," + "/a/e//,"
                + "/o/o/i/,/\\u00E8/e'//," + "/prova([^\\/]*?)ble/X$1Y/im/,"
                + "/X(.\\n)Y/P$1Q//," + "/\\/\\/,//");
        mailet.init(mci);

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        mail = new FakeMail(message);
        mailet.service(mail);

        Assert.assertEquals("une PRoVA", mail.getMessage().getSubject());
        Assert.assertEquals("Sto fecendo une PRoVA di scritture, e' solo une P.\n"
                + "Q ble ble ble.\n", mail.getMessage().getContent());

        // ------------------

        mailet = new ReplaceContent();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mci.setProperty("subjectPatternFile", "#/org/apache/james/mailet/standard/mailets/replaceSubject.patterns");
        mailet.init(mci);

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("re: r:ri:una prova");
        message.setText("Sto facendo una prova di scrittura/ \u00E8 solo una prova.\n"
                + "Bla bla bla bla.\n");

        mail = new FakeMail(message);
        mailet.service(mail);

        Assert.assertEquals("Re: Re: Re: una prova", mail.getMessage().getSubject());

        // ------------------

        mailet = new ReplaceContent();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mci.setProperty("bodyPattern", "/--messaggio originale--/<quote>/i/,"
                +
                // "/<quote>([^\\0]*)(\\r\\n)([^>]+)/<quote>$1$2>$3/imr/,"+
                "/<quote>(.*)(\\r\\n)([^>]+)/<quote>$1$2>$3/imrs/,"
                + "/<quote>\\r\\n//im/");
        mailet.init(mci);

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Prova.\r\n" + "\r\n" + "--messaggio originale--\r\n"
                + "parte del\r\n" + "messaggio\\ che\\0 deve0 essere\r\n"
                + "quotato. Vediamo se\r\n" + "ce la fa.");

        mail = new FakeMail(message);
        mailet.service(mail);

        Assert.assertEquals("una prova", mail.getMessage().getSubject());
        Assert.assertEquals("Prova.\r\n" + "\r\n" + ">parte del\r\n"
                + ">messaggio\\ che\\0 deve0 essere\r\n"
                + ">quotato. Vediamo se\r\n" + ">ce la fa.", mail.getMessage()
                .getContent());

        // ------------------

        mailet = new ReplaceContent();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mci.setProperty("bodyPattern", "/\\u2026/...//");
        mailet.init(mci);

        message = new MimeMessage(Session.getDefaultInstance(new Properties()));
        message.setSubject("una prova");
        message.setText("Prova \u2026 di replace \u2026");

        mail = new FakeMail(message);
        mailet.service(mail);

        Assert.assertEquals("una prova", mail.getMessage().getSubject());
        Assert.assertEquals("Prova ... di replace ...", mail.getMessage().getContent());
    }

    @Test
    public void testFromFakeCp1252Stream() throws MessagingException,
            IOException {
        String messageSource = "Content-Type: text/plain; charset=\"iso-8859-1\"\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "=93prova=94 con l=92apice";

        Mailet mailet;
        FakeMailetConfig mci;
        MimeMessage message;
        Mail mail;

        mailet = new ReplaceContent();
        mci = new FakeMailetConfig("Test", new FakeMailContext());
        mci.setProperty("bodyPattern", "/[\\u2018\\u2019\\u201A]/'//,"
                + "/[\\u201C\\u201D\\u201E]/\"//," + "/[\\x91\\x92\\x82]/'//,"
                + "/[\\x93\\x94\\x84]/\"//," + "/\\x85/...//," + "/\\x8B/<//,"
                + "/\\x9B/>//," + "/\\x96/-//," + "/\\x97/--//,");
        mailet.init(mci);

        message = new MimeMessage(Session.getDefaultInstance(new Properties()),
                new ByteArrayInputStream(messageSource.getBytes()));

        mail = new FakeMail(message);
        mailet.service(mail);

        Assert.assertEquals("\"prova\" con l'apice", mail.getMessage().getContent());
    }

}
