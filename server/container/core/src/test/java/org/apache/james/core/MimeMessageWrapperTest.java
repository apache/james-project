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
package org.apache.james.core;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.base.RFC2822Headers;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Enumeration;
import org.junit.After;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

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

        public MailHeaders getInnerHeaders() {
            return (MailHeaders) headers;
        }

        public boolean isHeadersLoadable() {
            return headersLoadable;
        }

        public void setHeadersLoadable(boolean headersLoadable) {
            this.headersLoadable = headersLoadable;
        }

        public boolean isMessageLoadable() {
            return messageLoadable;
        }

        public void setMessageLoadable(boolean messageLoadable) {
            this.messageLoadable = messageLoadable;
        }

        @Override
        protected synchronized void loadHeaders() throws MessagingException {
            if (headersLoadable) {
                super.loadHeaders();
            } else {
                throw new IllegalStateException("headersLoadable disabled");
            }
        }

        protected synchronized MailHeaders loadHeaders(InputStream is) throws MessagingException {
            if (headersLoadable) {
                return (MailHeaders) super.createInternetHeaders(is);
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
    final String content = "Subject: foo\r\nContent-Transfer-Encoding2: plain";
    final String sep = "\r\n\r\n";
    final String body = "bar\r\n";

    @Override
    protected MimeMessage getMessageFromSources(String sources) throws Exception {
        MimeMessageInputStreamSource mmis = null;
        try {
            mmis = new MimeMessageInputStreamSource("test", new SharedByteArrayInputStream(sources.getBytes()));
        } catch (MessagingException e) {
        }
        return new TestableMimeMessageWrapper(mmis);
    }

    @Before
    public void setUp() throws Exception {
        mw = (TestableMimeMessageWrapper) getMessageFromSources(content + sep + body);
    }

    @After
    public void tearDown() throws Exception {
        LifecycleUtil.dispose(mw);
    }

    @Test
    public void testDeferredMessageLoading() throws MessagingException, IOException {
        assertEquals("foo", mw.getSubject());
        assertFalse(mw.messageParsed());
        assertEquals("bar\r\n", mw.getContent());
        assertTrue(mw.messageParsed());
        assertFalse(mw.isModified());
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
        try {
            assertEquals("foo", mw.getSubject());
            fail("subject should not be loadable here, headers loading is disabled");
        } catch (IllegalStateException e) {
        }
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

    /*
     * Class under test for String getSubject()
     */
    @Test
    public void testGetSubjectFolding() {
        try {
            StringBuilder res = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(mw.getInputStream()));
            String line;
            while (r.ready()) {
                line = r.readLine();
                res.append(line).append("\r\n");
            }
            r.close();
            assertEquals(body, res.toString());
        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /*
     * Class under test for String getSubject()
     */
    @Test
    public void testAddHeaderAndSave() {
        try {
            mw.addHeader("X-Test", "X-Value");

            assertEquals("X-Value", mw.getHeader("X-Test")[0]);

            mw.saveChanges();

            ByteArrayOutputStream rawMessage = new ByteArrayOutputStream();
            mw.writeTo(rawMessage);

            assertEquals("X-Value", mw.getHeader("X-Test")[0]);

            String res = rawMessage.toString();

            boolean found = res.indexOf("X-Test: X-Value") > 0;
            assertEquals(true, found);

        } catch (MessagingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void testReplaceReturnPathOnBadMessage() throws Exception {
        MimeMessage message = getMessageWithBadReturnPath();
        message.setHeader(RFC2822Headers.RETURN_PATH, "<test@test.de>");
        Enumeration e = message.getMatchingHeaderLines(new String[]{"Return-Path"});
        assertEquals("Return-Path: <test@test.de>", e.nextElement());
        assertFalse(e.hasMoreElements());
        Enumeration h = message.getAllHeaderLines();
        assertEquals("Return-Path: <test@test.de>", h.nextElement());
        assertFalse(h.nextElement().toString().startsWith("Return-Path:"));
        LifecycleUtil.dispose(message);
    }

    @Test
    public void testAddReturnPathOnBadMessage() throws Exception {
        MimeMessage message = getMessageWithBadReturnPath();
        message.addHeader(RFC2822Headers.RETURN_PATH, "<test@test.de>");
        // test that we have now 2 return-paths
        Enumeration e = message.getMatchingHeaderLines(new String[]{"Return-Path"});
        assertEquals("Return-Path: <test@test.de>", e.nextElement());
        assertEquals("Return-Path: <mybadreturn@example.com>", e.nextElement());
        // test that return-path is the first line
        Enumeration h = message.getAllHeaderLines();
        assertEquals("Return-Path: <test@test.de>", h.nextElement());
        LifecycleUtil.dispose(message);
    }

    /**
     * Test for JAMES-1154
     */
    @Test
    public void testMessageStreamWithUpatedHeaders() throws MessagingException, IOException {
        mw.addHeader("X-Test", "X-Value");

        assertEquals("X-Value", mw.getHeader("X-Test")[0]);

        mw.saveChanges();

        BufferedReader reader = new BufferedReader(new InputStreamReader(mw.getMessageInputStream()));
        String line;

        boolean headerUpdated = false;
        while ((line = reader.readLine()) != null) {
            if (line.equals("X-Test: X-Value")) {
                headerUpdated = true;
                break;
            }
        }
        reader.close();
        assertTrue(headerUpdated);
    }

    /**
     * Test for JAMES-1154
     */
    @Test
    public void testMessageStreamWithUpatedContent() throws MessagingException, IOException {
        String newContent = "This is the new message content!";
        mw.setText(newContent);
        assertEquals(newContent, mw.getContent());

        mw.saveChanges();

        BufferedReader reader = new BufferedReader(new InputStreamReader(mw.getMessageInputStream()));
        String line;

        boolean contentUpdated = false;
        while ((line = reader.readLine()) != null) {
            if (line.equals(newContent)) {
                contentUpdated = true;
                break;
            }
        }
        reader.close();
        assertTrue(contentUpdated);
    }

    @Test
    public void testSize() throws MessagingException {
        assertEquals(body.length(), mw.getSize());
    }

    @Test
    public void testSizeModifiedHeaders() throws MessagingException {
        mw.addHeader("whatever", "test");
        assertEquals(body.length(), mw.getSize());
    }

    @Test
    public void testSizeModifiedBodyWithoutSave() throws MessagingException {
        String newBody = "This is the new body of the message";
        mw.setText(newBody);
        assertEquals(body.length(), mw.getSize());
    }

    @Test
    public void testSizeModifiedBodyWithSave() throws MessagingException {
        String newBody = "This is the new body of the message";
        mw.setText(newBody);
        mw.saveChanges();
        assertEquals(body.length(), mw.getSize());
    }
}
