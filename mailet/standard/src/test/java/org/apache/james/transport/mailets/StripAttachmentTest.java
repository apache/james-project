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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.io.FileUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;
import org.junit.Assert;
import org.junit.Test;

public class StripAttachmentTest {

    @Test
    public void testSimpleAttachment() throws MessagingException, IOException {
        Mailet mailet = initMailet();

        MimeMessage message = new MimeMessage(Session
                .getDefaultInstance(new Properties()));

        MimeMultipart mm = new MimeMultipart();
        MimeBodyPart mp = new MimeBodyPart();
        mp.setText("simple text");
        mm.addBodyPart(mp);
        String body = "\u0023\u00A4\u00E3\u00E0\u00E9";
        MimeBodyPart mp2 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\nContent-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                        + body).getBytes("UTF-8")));
        mp2.setDisposition("attachment");
        mp2.setFileName("10.tmp");
        mm.addBodyPart(mp2);
        String body2 = "\u0014\u00A3\u00E1\u00E2\u00E4";
        MimeBodyPart mp3 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\nContent-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                        + body2).getBytes("UTF-8")));
        mp3.setDisposition("attachment");
        mp3.setFileName("temp.zip");
        mm.addBodyPart(mp3);
        message.setSubject("test");
        message.setContent(mm);
        message.saveChanges();

        Mail mail = new FakeMail();
        mail.setMessage(message);

        mailet.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage,
                new String[]{"Bcc", "Content-Length", "Message-ID"});

        @SuppressWarnings("unchecked")
        Collection<String> c = (Collection<String>) mail
                .getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        Assert.assertNotNull(c);

        Assert.assertEquals(1, c.size());

        String name = c.iterator().next();

        File f = new File("./" + name);
        try {
            InputStream is = new FileInputStream(f);
            String savedFile = toString(is);
            is.close();
            Assert.assertEquals(body, savedFile);
        } finally {
            FileUtils.deleteQuietly(f);
        }
    }

    public String toString(final InputStream is) throws IOException {
        final ByteArrayOutputStream sw = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int n;
        while (-1 != (n = is.read(buffer))) {
            System.err.println(new String(buffer, 0, n));
            sw.write(buffer, 0, n);
        }
        return sw.toString("UTF-8");
    }

    @Test
    public void testSimpleAttachment2() throws MessagingException, IOException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty("directory", "./");
        mci.setProperty("remove", "all");
        mci.setProperty("notpattern", "^(winmail\\.dat$)");
        mailet.init(mci);

        MimeMessage message = new MimeMessage(Session
                .getDefaultInstance(new Properties()));

        MimeMultipart mm = new MimeMultipart();
        MimeBodyPart mp = new MimeBodyPart();
        mp.setText("simple text");
        mm.addBodyPart(mp);
        String body = "\u0023\u00A4\u00E3\u00E0\u00E9";
        MimeBodyPart mp2 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\n\r\n" + body).getBytes("UTF-8")));
        mp2.setDisposition("attachment");
        mp2.setFileName("temp.tmp");
        mm.addBodyPart(mp2);
        String body2 = "\u0014\u00A3\u00E1\u00E2\u00E4";
        MimeBodyPart mp3 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\n\r\n" + body2).getBytes("UTF-8")));
        mp3.setDisposition("attachment");
        mp3.setFileName("winmail.dat");
        mm.addBodyPart(mp3);
        message.setSubject("test");
        message.setContent(mm);
        message.saveChanges();

        Mail mail = new FakeMail();
        mail.setMessage(message);

        mailet.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage,
                new String[]{"Bcc", "Content-Length", "Message-ID"});
        // String res = rawMessage.toString();

        @SuppressWarnings("unchecked")
        Collection<String> c = (Collection<String>) mail
                .getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        Assert.assertNotNull(c);

        Assert.assertEquals(1, c.size());

        String name = c.iterator().next();

        File f = new File("./" + name);
        try {
            InputStream is = new FileInputStream(f);
            String savedFile = toString(is);
            is.close();
            Assert.assertEquals(body, savedFile);
        } finally {
            FileUtils.deleteQuietly(f);
        }
    }

    @Test
    public void testSimpleAttachment3() throws MessagingException, IOException {
        Mailet mailet = initMailet();

        // System.setProperty("mail.mime.decodefilename", "true");

        MimeMessage message = new MimeMessage(Session
                .getDefaultInstance(new Properties()));

        MimeMultipart mm = new MimeMultipart();
        MimeBodyPart mp = new MimeBodyPart();
        mp.setText("simple text");
        mm.addBodyPart(mp);
        String body = "\u0023\u00A4\u00E3\u00E0\u00E9";
        MimeBodyPart mp2 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\n\r\n" + body).getBytes("UTF-8")));
        mp2.setDisposition("attachment");
        mp2
                .setFileName("=?iso-8859-15?Q?=E9_++++Pubblicit=E0_=E9_vietata____Milano9052.tmp?=");
        mm.addBodyPart(mp2);
        String body2 = "\u0014\u00A3\u00E1\u00E2\u00E4";
        MimeBodyPart mp3 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\n\r\n" + body2).getBytes("UTF-8")));
        mp3.setDisposition("attachment");
        mp3.setFileName("temp.zip");
        mm.addBodyPart(mp3);
        message.setSubject("test");
        message.setContent(mm);
        message.saveChanges();

        // message.writeTo(System.out);
        // System.out.println("--------------------------\n\n\n");

        Mail mail = new FakeMail();
        mail.setMessage(message);

        mailet.service(mail);

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mail.getMessage().writeTo(rawMessage,
                new String[]{"Bcc", "Content-Length", "Message-ID"});
        // String res = rawMessage.toString();

        @SuppressWarnings("unchecked")
        Collection<String> c = (Collection<String>) mail
                .getAttribute(StripAttachment.SAVED_ATTACHMENTS_ATTRIBUTE_KEY);
        Assert.assertNotNull(c);

        Assert.assertEquals(1, c.size());

        String name = c.iterator().next();
        // System.out.println("--------------------------\n\n\n");
        // System.out.println(name);

        Assert.assertTrue(name.startsWith("e_Pubblicita_e_vietata_Milano9052"));

        File f = new File("./" + name);
        try {
            InputStream is = new FileInputStream(f);
            String savedFile = toString(is);
            is.close();
            Assert.assertEquals(body, savedFile);
        } finally {
            FileUtils.deleteQuietly(f);
        }
    }

    @Test
    public void testToAndFromAttributes() throws MessagingException,
            IOException {
        Mailet strip = new StripAttachment();
        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty("attribute", "my.attribute");
        mci.setProperty("remove", "all");
        mci.setProperty("notpattern", ".*\\.tmp.*");
        strip.init(mci);

        Mailet recover = new RecoverAttachment();
        FakeMailetConfig mci2 = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci2.setProperty("attribute", "my.attribute");
        recover.init(mci2);

        Mailet onlyText = new OnlyText();
        onlyText.init(new FakeMailetConfig("Test", new FakeMailContext()));

        MimeMessage message = new MimeMessage(Session
                .getDefaultInstance(new Properties()));

        MimeMultipart mm = new MimeMultipart();
        MimeBodyPart mp = new MimeBodyPart();
        mp.setText("simple text");
        mm.addBodyPart(mp);
        String body = "\u0023\u00A4\u00E3\u00E0\u00E9";
        MimeBodyPart mp2 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\nContent-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                        + body).getBytes("UTF-8")));
        mp2.setDisposition("attachment");
        mp2
                .setFileName("=?iso-8859-15?Q?=E9_++++Pubblicit=E0_=E9_vietata____Milano9052.tmp?=");
        mm.addBodyPart(mp2);
        String body2 = "\u0014\u00A3\u00E1\u00E2\u00E4";
        MimeBodyPart mp3 = new MimeBodyPart(new ByteArrayInputStream(
                ("Content-Transfer-Encoding: 8bit\r\nContent-Type: application/octet-stream; charset=utf-8\r\n\r\n"
                        + body2).getBytes("UTF-8")));
        mp3.setDisposition("attachment");
        mp3.setFileName("temp.zip");
        mm.addBodyPart(mp3);
        message.setSubject("test");
        message.setContent(mm);
        message.saveChanges();
        Mail mail = new FakeMail();
        mail.setMessage(message);

        Assert.assertTrue(mail.getMessage().getContent() instanceof MimeMultipart);
        Assert.assertEquals(3, ((MimeMultipart) mail.getMessage().getContent())
                .getCount());

        strip.service(mail);

        Assert.assertTrue(mail.getMessage().getContent() instanceof MimeMultipart);
        Assert.assertEquals(1, ((MimeMultipart) mail.getMessage().getContent())
                .getCount());

        onlyText.service(mail);

        Assert.assertFalse(mail.getMessage().getContent() instanceof MimeMultipart);

        Assert.assertEquals("simple text", mail.getMessage().getContent());

        // prova per caricare il mime message da input stream che altrimenti
        // javamail si comporta differentemente.
        String mimeSource = "Message-ID: <26194423.21197328775426.JavaMail.bago@bagovista>\r\nSubject: test\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=us-ascii\r\nContent-Transfer-Encoding: 7bit\r\n\r\nsimple text";

        MimeMessage mmNew = new MimeMessage(Session
                .getDefaultInstance(new Properties()),
                new ByteArrayInputStream(mimeSource.getBytes("UTF-8")));

        mmNew.writeTo(System.out);
        mail.setMessage(mmNew);

        recover.service(mail);

        Assert.assertTrue(mail.getMessage().getContent() instanceof MimeMultipart);
        Assert.assertEquals(2, ((MimeMultipart) mail.getMessage().getContent())
                .getCount());

        Object actual = ((MimeMultipart) mail.getMessage().getContent())
                .getBodyPart(1).getContent();
        if (actual instanceof ByteArrayInputStream) {
            Assert.assertEquals(body2, toString((ByteArrayInputStream) actual));
        } else {
            Assert.assertEquals(body2, actual);
        }

    }

    private Mailet initMailet() throws MessagingException {
        Mailet mailet = new StripAttachment();

        FakeMailetConfig mci = new FakeMailetConfig("Test",
                new FakeMailContext());
        mci.setProperty("directory", "./");
        mci.setProperty("remove", "all");
        mci.setProperty("pattern", ".*\\.tmp");
        mci.setProperty("decodeFilename", "true");
        mci.setProperty("replaceFilenamePattern",
                "/[\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5]/A//,"
                        + "/[\u00C6]/AE//,"
                        + "/[\u00C8\u00C9\u00CA\u00CB]/E//,"
                        + "/[\u00CC\u00CD\u00CE\u00CF]/I//,"
                        + "/[\u00D2\u00D3\u00D4\u00D5\u00D6]/O//,"
                        + "/[\u00D7]/x//," + "/[\u00D9\u00DA\u00DB\u00DC]/U//,"
                        + "/[\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5]/a//,"
                        + "/[\u00E6]/ae//,"
                        + "/[\u00E8\u00E9\u00EA\u00EB]/e//,"
                        + "/[\u00EC\u00ED\u00EE\u00EF]/i//,"
                        + "/[\u00F2\u00F3\u00F4\u00F5\u00F6]/o//,"
                        + "/[\u00F9\u00FA\u00FB\u00FC]/u//,"
                        + "/[^A-Za-z0-9._-]+/_//");

        mailet.init(mci);
        return mailet;
    }

}
