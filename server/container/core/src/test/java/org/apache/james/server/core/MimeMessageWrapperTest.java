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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import java.util.Properties;

import com.google.common.collect.ImmutableList;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.MimeMessageUtil;
import org.apache.mailet.base.RFC2822Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test the subject folding issue.
 */
public class MimeMessageWrapperTest extends MimeMessageFromStreamTest {

    private final class TestableMimeMessageWrapper extends MimeMessageWrapper {

        boolean messageLoadable = true;
        boolean headersLoadable = true;

        private TestableMimeMessageWrapper(MimeMessageSource source) throws MessagingException {
            super(source);
        }

        public boolean messageParsed() {
            return messageParsed;
        }

        public void setHeadersLoadable(boolean headersLoadable) {
            this.headersLoadable = headersLoadable;
        }

        @Override
        protected synchronized void loadHeaders() throws MessagingException {
            if (headersLoadable) {
                super.loadHeaders();
            } else {
                throw new IllegalStateException("headersLoadable disabled");
            }
        }

        @Override
        public synchronized void loadMessage() throws MessagingException {
            if (messageLoadable) {
                super.loadMessage();
            } else {
                throw new IllegalStateException("messageLoadable disabled");
            }
        }
    }
    
    TestableMimeMessageWrapper mw = null;
    TestableMimeMessageWrapper onlyHeader = null;
    final String content = "Subject: foo\r\nContent-Transfer-Encoding2: plain";
    final String sep = "\r\n\r\n";
    final String body = "bar\r\n";

    @Override
    protected TestableMimeMessageWrapper getMessageFromSources(String sources) throws Exception {
        MimeMessageInputStreamSource mmis = MimeMessageInputStreamSource.create("test", new SharedByteArrayInputStream(sources.getBytes()));
        return new TestableMimeMessageWrapper(mmis);
    }

    @BeforeEach
    public void setUp() throws Exception {
        mw = getMessageFromSources(content + sep + body);
        onlyHeader = getMessageFromSources(content);

        ContentTypeCleaner.initialize();
    }

    @AfterEach
    public void tearDown() throws Exception {
        LifecycleUtil.dispose(mw);
        LifecycleUtil.dispose(onlyHeader);
    }

    @Test
    void testMessageWithWrongContentTypeShouldNotThrow() throws Exception {
        MimeMessageWrapper mmw = new MimeMessageWrapper(mw);
        new MimeMessageWrapper(mw).addHeader("Content-Type", "file;name=\"malformed.pdf\"");
        mmw.saveChanges();
    }

    @Test
    void testMessageWithStarContentTypeShouldNotThrow() throws Exception {
        MimeMessageWrapper mmw = new MimeMessageWrapper(mw);
        new MimeMessageWrapper(mw).addHeader("Content-Type", "image/*; name=\"20230720_175854.jpg\"");
        mmw.saveChanges();
    }

    @Test
    public void testDeferredMessageLoading() throws MessagingException, IOException {
        assertThat(mw.getSubject()).isEqualTo("foo");
        assertThat(mw.messageParsed()).isFalse();
        assertThat(mw.getContent()).isEqualTo("bar\r\n");
        assertThat(mw.messageParsed()).isTrue();
        assertThat(mw.isModified()).isFalse();
    }

    /**
     * this is commented out due optimisation reverts (JAMES-559) public void
     * testDeferredMessageLoadingWhileWriting() throws MessagingException,
     * IOException { mw.setMessageLoadable(false);
     * assertEquals("foo",mw.getSubject()); assertFalse(mw.isModified());
     * mw.setSubject("newSubject"); assertEquals("newSubject",mw.getSubject());
     * assertFalse(mw.messageParsed()); assertTrue(mw.isModified());
     * mw.setMessageLoadable(true);
     * 
     * }
     */
    @Test
    public void testDeferredHeaderLoading() throws MessagingException, IOException {
        mw.setHeadersLoadable(false);

        assertThatThrownBy(() -> mw.getSubject()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * See JAMES-474 MimeMessageWrapper(MimeMessage) should clone the original
     * message.
     */
    @Test
    public void testMessageCloned() throws MessagingException, IOException, InterruptedException {
        MimeMessageWrapper mmw = new MimeMessageWrapper(mw);
        LifecycleUtil.dispose(mw);
        mw = null;
        System.gc();
        Thread.sleep(200);
        mmw.writeTo(System.out);
    }

    @Test
    public void testGetSubjectFolding() throws Exception {
        StringBuilder res = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(mw.getInputStream()));
        String line;
        while (r.ready()) {
            line = r.readLine();
            res.append(line).append("\r\n");
        }
        r.close();
        assertThat(res.toString()).isEqualTo(body);
    }

    @Test
    public void testAddHeaderAndSave() throws Exception {
        mw.addHeader("X-Test", "X-Value");

        assertThat(mw.getHeader("X-Test")[0]).isEqualTo("X-Value");

        mw.saveChanges();

        ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
        mw.writeTo(rawMessage);

        assertThat(mw.getHeader("X-Test")[0]).isEqualTo("X-Value");

        String res = rawMessage.toString();

        boolean found = res.indexOf("X-Test: X-Value") > 0;
        assertThat(found).isTrue();
    }

    @Test
    public void testReplaceReturnPathOnBadMessage() throws Exception {
        MimeMessage message = getMessageWithBadReturnPath();
        message.setHeader(RFC2822Headers.RETURN_PATH, "<test@test.de>");
        Enumeration<String> e = message.getMatchingHeaderLines(new String[]{"Return-Path"});
        assertThat(e.nextElement()).isEqualTo("Return-Path: <test@test.de>");
        assertThat(e.hasMoreElements()).isFalse();
        Enumeration<String> h = message.getAllHeaderLines();
        assertThat(h.nextElement()).isEqualTo("Return-Path: <test@test.de>");
        assertThat(h.nextElement().startsWith("Return-Path:")).isFalse();
        LifecycleUtil.dispose(message);
    }

    @Test
    public void testAddReturnPathOnBadMessage() throws Exception {
        MimeMessage message = getMessageWithBadReturnPath();
        message.addHeader(RFC2822Headers.RETURN_PATH, "<test@test.de>");
        // test that we have now 2 return-paths
        Enumeration<String> e = message.getMatchingHeaderLines(new String[]{"Return-Path"});
        assertThat(e.nextElement()).isEqualTo("Return-Path: <test@test.de>");
        assertThat(e.nextElement()).isEqualTo("Return-Path: <mybadreturn@example.com>");
        // test that return-path is the first line
        Enumeration<String> h = message.getAllHeaderLines();
        assertThat(h.nextElement()).isEqualTo("Return-Path: <test@test.de>");
        LifecycleUtil.dispose(message);
    }

    /**
     * Test for JAMES-1154
     */
    @Test
    public void testMessageStreamWithUpdatedHeaders() throws MessagingException, IOException {
        mw.addHeader("X-Test", "X-Value");

        assertThat(mw.getHeader("X-Test")[0]).isEqualTo("X-Value");

        mw.saveChanges();

        BufferedReader reader = new BufferedReader(new InputStreamReader(mw.getMessageInputStream()));

        boolean headerUpdated = reader.lines()
            .anyMatch(line -> line.equals("X-Test: X-Value"));
        reader.close();
        assertThat(headerUpdated).isTrue();
    }

    /**
     * Test for JAMES-1154
     */
    @Test
    public void testMessageStreamWithUpatedContent() throws MessagingException, IOException {
        String newContent = "This is the new message content!";
        mw.setText(newContent);
        assertThat(mw.getContent()).isEqualTo(newContent);

        mw.saveChanges();

        BufferedReader reader = new BufferedReader(new InputStreamReader(mw.getMessageInputStream()));

        boolean contentUpdated = reader.lines()
            .anyMatch(line -> line.equals(newContent));
        reader.close();
        assertThat(contentUpdated).isTrue();
    }

    @Test
    public void testSize() throws MessagingException {
        assertThat(mw.getSize()).isEqualTo(body.length());
    }

    @Test
    public void getSizeShouldReturnZeroWhenNoHeaderAndAddHeader() throws MessagingException {
        onlyHeader.addHeader("a", "b");
        assertThat(onlyHeader.getSize()).isEqualTo(0);
    }

    @Test
    public void getSizeShouldReturnZeroWhenNoHeader() throws MessagingException {
        assertThat(onlyHeader.getSize()).isEqualTo(0);
    }

    @Test
    public void getSizeShouldReturnZeroWhenSingleChar() throws Exception {
        TestableMimeMessageWrapper message = getMessageFromSources("a");
        assertThat(message.getSize()).isEqualTo(0);
        LifecycleUtil.dispose(message);
    }

    @Test
    public void getSizeShouldReturnZeroWhenSingleCharBody() throws Exception {
        TestableMimeMessageWrapper message = getMessageFromSources("a: b\r\n\r\na");
        assertThat(message.getSize()).isEqualTo(1);
        LifecycleUtil.dispose(message);
    }

    @Test
    public void getSizeShouldReturnZeroWhenEmptyString() throws Exception {
        TestableMimeMessageWrapper message = getMessageFromSources("");
        assertThat(message.getSize()).isEqualTo(0);
        LifecycleUtil.dispose(message);
    }

    @Test
    public void getMessageSizeShouldReturnExpectedValueWhenNoHeader() throws MessagingException {
        assertThat(onlyHeader.getMessageSize()).isEqualTo(content.length());
    }

    @Test
    public void getMessageSizeShouldReturnExpectedValueWhenNoHeaderAndAddHeader() throws Exception {
        onlyHeader.addHeader("new", "value");
        assertThat(onlyHeader.getMessageSize()).isEqualTo(
            IOUtils.consume(onlyHeader.getMessageInputStream()));
    }

    @Test
    public void getMessageSizeShouldReturnExpectedValueWhenSingleChar() throws Exception {
        TestableMimeMessageWrapper message = getMessageFromSources("a");
        assertThat(message.getMessageSize()).isEqualTo(1);
        LifecycleUtil.dispose(message);
    }

    @Test
    public void getMessageSizeShouldReturnExpectedValueWhenEmptyString() throws Exception {
        TestableMimeMessageWrapper message = getMessageFromSources("");
        assertThat(message.getMessageSize()).isEqualTo(0);
        LifecycleUtil.dispose(message);
    }

    @Test
    public void testSizeModifiedHeaders() throws MessagingException {
        mw.addHeader("whatever", "test");
        assertThat(mw.getSize()).isEqualTo(body.length());
    }

    @Test
    public void testSizeModifiedBodyWithoutSave() throws MessagingException {
        String newBody = "This is the new body of the message";
        mw.setText(newBody);
        assertThat(mw.getSize()).isEqualTo(-1);
    }

    @Test
    public void testSizeModifiedBodyWithSave() throws MessagingException {
        String newBody = "This is the new body of the message";
        mw.setText(newBody);
        mw.saveChanges();
        assertThat(mw.getSize()).isEqualTo(-1);
    }
    
    @Test
    public void jiraJames1593() throws MessagingException, IOException {
        Properties noProperties = new Properties();
        Session session = Session.getDefaultInstance(noProperties);
        InputStream stream = ClassLoader.getSystemResourceAsStream("JAMES-1593.eml");
        MimeMessage message = new MimeMessage(session, stream);
        MimeMessageWrapper wrapper = new MimeMessageWrapper(message);
        assertThat(wrapper.getEncoding()).isEqualTo("\"base64\"");
        LifecycleUtil.dispose(wrapper);
    }

    @Test
    public void saveChangesShouldPreserveMessageId() throws Exception {
        String messageId = "<5436@ab.com>";
        MimeMessage message = MimeMessageUtil.mimeMessageFromString("Message-ID: " + messageId + "\r\n" +
            "Subject: test\r\n" +
            "\r\n" +
            "Content!");

        MimeMessageWrapper mimeMessageWrapper = new MimeMessageWrapper(message);

        mimeMessageWrapper.saveChanges();

        assertThat(mimeMessageWrapper.getMessageID())
            .isEqualTo(messageId);
        LifecycleUtil.dispose(mimeMessageWrapper);
    }

    @Test
    public void getMessageSizeShouldBeAccurateWhenHeadersAreModified() throws Exception {
        MimeMessageWrapper wrapper = new MimeMessageWrapper(MimeMessageInputStreamSource.create(MailImpl.getId(),
            ClassLoaderUtils.getSystemResourceAsSharedStream("JAMES-1593.eml")));
        wrapper.setHeader("header", "vss");

        assertThat(wrapper.getMessageSize()).isEqualTo(
            IOUtils.consume(wrapper.getMessageInputStream()));
        LifecycleUtil.dispose(wrapper);
    }

    @Test
    public void getMessageSizeShouldBeAccurateWhenHeadersAreModifiedAndOtherEncoding() throws Exception {
        MimeMessageWrapper wrapper = new MimeMessageWrapper(MimeMessageInputStreamSource.create(MailImpl.getId(),
            ClassLoaderUtils.getSystemResourceAsSharedStream("mail-containing-unicode-characters.eml")));
        wrapper.setHeader("header", "vss");

        assertThat(wrapper.getMessageSize()).isEqualTo(
            IOUtils.consume(wrapper.getMessageInputStream()));
        LifecycleUtil.dispose(wrapper);
    }
}
