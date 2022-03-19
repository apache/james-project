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
package org.apache.james.server.core;

import static org.apache.mailet.base.RFC2822Headers.RETURN_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.RFC2822Headers;
import org.junit.jupiter.api.Test;

public class MimeMessageTest {

    protected MimeMessage getSimpleMessage() throws Exception {
        return MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("Date", "Tue, 16 Jan 2018 09:56:01 +0700 (ICT)")
            .setSubject("test")
            .setText("test body", "text/plain; charset=us-ascii")
            .build();
    }

    protected String getSimpleMessageCleanedSource() {
        return "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Content-Type: text/plain; charset=us-ascii\r\n"
                + "Content-Transfer-Encoding: 7bit\r\n"
                + "\r\n"
                + "test body";
    }

    protected MimeMessage getMessageWithBadReturnPath() throws Exception {
        return MimeMessageBuilder.mimeMessageBuilder()
            .addHeader("Date", "Tue, 16 Jan 2018 09:56:01 +0700 (ICT)")
            .setSubject("test")
            .addHeader(RFC2822Headers.RETURN_PATH, "<mybadreturn@example.com>")
            .setText("test body")
            .build();
    }

    protected String getMessageWithBadReturnPathSource() {
        return "Subject: test\r\n"
                + "Return-Path: <mybadreturn@example.com>\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Content-Type: text/plain; charset=us-ascii\r\n"
                + "Content-Transfer-Encoding: 7bit\r\n"
                + "\r\n"
                + "test body";
    }

    protected String getSimpleMessageCleanedSourceHeaderExpected() {
        return "X-Test: foo\r\n" + getSimpleMessageCleanedSource();
    }

    @Test
    public void testSimpleMessage() throws Exception {
        MimeMessage m = getSimpleMessage();
        assertThat(getCleanedMessageSource(m)).isEqualTo(getSimpleMessageCleanedSource());
        LifecycleUtil.dispose(m);
    }

    protected MimeMessage getMultipartMessage() throws Exception {
        MimeMessage mmCreated = MimeMessageUtil.defaultMimeMessage();
        mmCreated.setSubject("test");
        mmCreated.addHeader("Date", "Tue, 16 Jan 2018 09:56:01 +0700 (ICT)");
        MimeMultipart mm = new MimeMultipart("alternative");
        mm.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream("X-header: test1\r\nContent-Type: text/plain; charset=Cp1252\r\n"
                .getBytes())), "first part òàù".getBytes()));
        mm.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream("X-header: test2\r\nContent-Type: text/plain; charset=Cp1252\r\nContent-Transfer-Encoding: quoted-printable\r\n"
                .getBytes())), "second part =E8=E8".getBytes()));
        mmCreated.setContent(mm);
        mmCreated.saveChanges();
        return mmCreated;
    }

    protected String getMultipartMessageSource() {
        return "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative; \r\n"
                + "\tboundary=\"----=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\"\r\n"
                + "\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test1\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "first part =E8\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test2\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "second part =E8=E8\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX--\r\n";
    }

    protected String getMultipartMessageExpected1() {
        return "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Content-Type: multipart/alternative; \r\n"
                + "\tboundary=\"----=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\"\r\n"
                + "\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test1\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "test=80\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test2\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "second part =E8=E8\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX--\r\n";
    }

    protected String getMultipartMessageExpected2() {
        return "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Content-Type: multipart/alternative; \r\n"
                + "\tboundary=\"----=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\"\r\n"
                + "\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test1\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "test=80\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test2\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "second part =E8=E8\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "Subject: test3\r\n"
                + "\r\n"
                + "third part\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX--\r\n";
    }

    protected String getMultipartMessageExpected3() {
        return "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Date: Tue, 16 Jan 2018 09:56:01 +0700 (ICT)\r\n"
                + "Content-Type: binary/octet-stream\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "mynewco=F2=E0=F9ntent=80=E0!";
    }

    @Test
    public void testMultipartMessageChanges() throws Exception {

        MimeMessage mm = getMultipartMessage();

        MimeMultipart content1 = (MimeMultipart) mm.getContent();
        BodyPart b1 = content1.getBodyPart(0);
        b1.setContent("test€", "text/plain; charset=Cp1252");
        mm.setContent(content1, mm.getContentType());
        // .setHeader(RFC2822Headers.CONTENT_TYPE,contentType);
        mm.saveChanges();

        assertThat(getCleanedMessageSource(mm)).isEqualTo(getMultipartMessageExpected1());

        MimeMultipart content2 = (MimeMultipart) mm.getContent();
        content2.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream(
                "Subject: test3\r\n".getBytes())), "third part".getBytes()));
        mm.setContent(content2, mm.getContentType());
        mm.saveChanges();

        assertThat(getCleanedMessageSource(mm)).isEqualTo(getMultipartMessageExpected2());

        mm.setContent("mynewcoòàùntent€à!", "text/plain; charset=cp1252");
        mm.setHeader(RFC2822Headers.CONTENT_TYPE, "binary/octet-stream");
        // mm.setHeader("Content-Transfer-Encoding","8bit");
        mm.saveChanges();

        assertThat(getCleanedMessageSource(mm)).isEqualTo(getMultipartMessageExpected3());

        LifecycleUtil.dispose(mm);

    }

    protected String getMissingEncodingAddHeaderSource() {
        return "Subject: test\r\n"
                + "\r\n"
                + "Testà\r\n";
    }

    /**
     * This test is not usable in different locale environment.
     */
    /*
     * public void testMissingEncodingAddHeader() throws Exception {
     * 
     * 
     * MimeMessage mm = getMissingEncodingAddHeaderMessage();
     * mm.setHeader("Content-Transfer-Encoding", "quoted-printable");
     * mm.saveChanges();
     * 
     * assertEquals(getMissingEncodingAddHeaderExpected(),getCleanedMessageSource
     * (mm)); }
     */
    protected String getCleanedMessageSource(MimeMessage mm) throws Exception {
        ByteArrayOutputStream out2;
        out2 = new ByteArrayOutputStream();
        mm.writeTo(out2, new String[]{"Message-ID"});

        String res = out2.toString();

        int p = res.indexOf("\r\n\r\n");
        if (p > 0) {
            String head = res.substring(0, p);
            String[] str = head.split("\r\n");
            Arrays.sort(str);
            StringBuilder outputHead = new StringBuilder();
            for (int i = str.length - 1; i >= 0; i--) {
                outputHead.append(str[i]);
                outputHead.append("\r\n");
            }
            outputHead.append(res.substring(p + 2));
            res = outputHead.toString();
        }

        res = res.replaceAll("----=_Part_\\d*_\\d+\\.\\d+", "----=_Part_\\0_XXXXXXXXXXX.XXXXXXXXXXX");
        return res;
    }

    protected MimeMessage getMissingEncodingMessage() throws Exception {
        MimeMessage mmCreated = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mmCreated.setSubject("test");
        MimeMultipart mm = new MimeMultipart("alternative");
        mm.addBodyPart(new MimeBodyPart(new InternetHeaders(new ByteArrayInputStream("X-header: test2\r\nContent-Type: text/plain; charset=Cp1252\r\nContent-Transfer-Encoding: quoted-printable\r\n"
                .getBytes())), "second part =E8=E8".getBytes()));
        mmCreated.setContent(mm);
        mmCreated.saveChanges();
        return mmCreated;
    }

    protected String getMissingEncodingMessageSource() {
        return "Subject: test\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/alternative; \r\n"
                + "\tboundary=\"----=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\"\r\n"
                + "\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX\r\n"
                + "X-header: test2\r\n"
                + "Content-Type: text/plain; charset=Cp1252\r\n"
                + "Content-Transfer-Encoding: quoted-printable\r\n"
                + "\r\n"
                + "second part =E8=E8\r\n"
                + "------=_Part_0_XXXXXXXXXXX.XXXXXXXXXXX--\r\n";
    }

    @Test
    public void testGetLineCount() throws Exception {
        MimeMessage mm = getMissingEncodingMessage();
        try {
            int count = mm.getLineCount();
            assertThat(count == -1 || count == 7).isTrue();
        } catch (Exception e) {
            fail("Unexpected exception in getLineCount");
        }
        LifecycleUtil.dispose(mm);
    }

    @Test
    public void testReturnPath() throws Exception {
        MimeMessage message = getSimpleMessage();
        assertThat(message.getHeader(RETURN_PATH)).isNull();
        LifecycleUtil.dispose(message);
    }

    @Test
    public void testHeaderOrder() throws Exception {
        MimeMessage message = getSimpleMessage();
        message.setHeader(RFC2822Headers.RETURN_PATH, "<test@test.de>");
        Enumeration<String> h = message.getAllHeaderLines();

        assertThat("Return-Path: <test@test.de>").isEqualTo(h.nextElement());
        LifecycleUtil.dispose(message);
    }

    /**
     * http://issues.apache.org/jira/browse/GERONIMO-4261
     * 
     * This bug was in geronimo-javamail_1.4-1.5 Has been fixed in
     * geronimo-javamail_1.4-1.6
     */
    @Test
    public void testGeronimoIndexOutOfBounds() throws Exception {
        String message = "                  \r\n" + "Subject: test\r\n" + "\r\n" + "Body\r\n";

        byte[] messageBytes = message.getBytes(StandardCharsets.US_ASCII);
        new MimeMessage(null, new ByteArrayInputStream(messageBytes));
    }
}
