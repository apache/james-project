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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.junit.Test;

public class MimeMessageCopyOnWriteProxyTest extends MimeMessageFromStreamTest {

    final String content = "Subject: foo\r\nContent-Transfer-Encoding2: plain";
    final String sep = "\r\n\r\n";
    final String body = "bar\r\n.\r\n";

    @Override
    protected MimeMessage getMessageFromSources(String sources) throws Exception {
        MimeMessageInputStreamSource mmis = new MimeMessageInputStreamSource("test", new SharedByteArrayInputStream(sources.getBytes()));
        return new MimeMessageCopyOnWriteProxy(mmis);
        // return new MimeMessage(Session.getDefaultInstance(new
        // Properties()),new ByteArrayInputStream(sources.getBytes()));
    }

    @Test
    public void testMessageCloning1() throws Exception {
        ArrayList<MailAddress> r = new ArrayList<>();
        r.add(new MailAddress("recipient@test.com"));
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                content + sep + body);
        MailImpl mail = new MailImpl("test", new MailAddress("test@test.com"), r, messageFromSources);
        MailImpl m2 = (MailImpl) mail.duplicate();
        System.out.println("mail: " + getReferences(mail.getMessage()) + " m2: " + getReferences(m2.getMessage()));
        assertNotSame(m2, mail);
        assertNotSame(m2.getMessage(), mail.getMessage());
        // test that the wrapped message is the same
        assertTrue(isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        // test it is the same after read only operations!
        mail.getMessage().getSubject();
        assertTrue(isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        mail.getMessage().setText("new body");
        mail.getMessage().saveChanges();
        // test it is different after a write operation!
        mail.getMessage().setSubject("new Subject");
        assertTrue(!isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(m2);
        LifecycleUtil.dispose(messageFromSources);
    }

    @Test
    public void testMessageCloning2() throws Exception {
        ArrayList<MailAddress> r = new ArrayList<>();
        r.add(new MailAddress("recipient@test.com"));
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                content + sep + body);
        MailImpl mail = new MailImpl("test", new MailAddress("test@test.com"), r, messageFromSources);
        MailImpl m2 = (MailImpl) mail.duplicate();
        System.out.println("mail: " + getReferences(mail.getMessage()) + " m2: " + getReferences(m2.getMessage()));
        assertNotSame(m2, mail);
        assertNotSame(m2.getMessage(), mail.getMessage());
        // test that the wrapped message is the same
        assertTrue(isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        // test it is the same after real only operations!
        m2.getMessage().getSubject();
        assertTrue(isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        m2.getMessage().setText("new body");
        m2.getMessage().saveChanges();
        // test it is different after a write operation!
        m2.getMessage().setSubject("new Subject");
        assertTrue(!isSameMimeMessage(m2.getMessage(), mail.getMessage()));
        // check that the subjects are correct on both mails!
        assertEquals(m2.getMessage().getSubject(), "new Subject");
        assertEquals(mail.getMessage().getSubject(), "foo");
        // cloning again the messages
        Mail m2clone = m2.duplicate();
        assertTrue(isSameMimeMessage(m2clone.getMessage(), m2.getMessage()));
        MimeMessage mm = getWrappedMessage(m2.getMessage());
        assertNotSame(m2.getMessage(), m2clone.getMessage());
        // test that m2clone has a valid wrapped message
        MimeMessage mm3 = getWrappedMessage(m2clone.getMessage());
        assertNotNull(mm3);
        // dispose m2 and check that the clone has still a valid message and it
        // is the same!
        LifecycleUtil.dispose(m2);
        assertEquals(mm3, getWrappedMessage(m2clone.getMessage()));
        // change the message that should be not referenced by m2 that has
        // been disposed, so it should not clone it!
        m2clone.getMessage().setSubject("new Subject 2");
        m2clone.getMessage().setText("new Body 3");
        assertTrue(isSameMimeMessage(m2clone.getMessage(), mm));
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
    }

    /**
     * If I create a new MimeMessageCopyOnWriteProxy from another
     * MimeMessageCopyOnWriteProxy, I remove references to the first and I
     * change the second, then it should not clone
     */
    @Test
    public void testMessageAvoidCloning() throws Exception {
        ArrayList<MailAddress> r = new ArrayList<>();
        r.add(new MailAddress("recipient@test.com"));
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                content + sep + body);
        MailImpl mail = new MailImpl("test", new MailAddress("test@test.com"), r, messageFromSources);
        // cloning the message
        Mail mailClone = mail.duplicate();
        assertTrue(isSameMimeMessage(mailClone.getMessage(), mail.getMessage()));
        MimeMessage mm = getWrappedMessage(mail.getMessage());
        assertNotSame(mail.getMessage(), mailClone.getMessage());
        // dispose mail and check that the clone has still a valid message and
        // it is the same!
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
        // need to add a gc and a wait, because the original mimemessage should
        // be finalized before the test.
        System.gc();
        Thread.sleep(1000);
        // dumb test
        assertTrue(isSameMimeMessage(mailClone.getMessage(), mailClone.getMessage()));
        // change the message that should be not referenced by mail that has
        // been disposed, so it should not clone it!
        mailClone.getMessage().setSubject("new Subject 2");
        mailClone.getMessage().setText("new Body 3");
        assertTrue(isSameMimeMessage(mailClone.getMessage(), mm));
        LifecycleUtil.dispose(mailClone);
        LifecycleUtil.dispose(mm);
    }

    /**
     * If I create a new MimeMessageCopyOnWriteProxy from a MimeMessage and I
     * change the new message, the original should be unaltered and the proxy
     * should clone the message.
     */
    @Test
    public void testMessageCloning3() throws Exception {
        ArrayList<MailAddress> r = new ArrayList<>();
        r.add(new MailAddress("recipient@test.com"));
        MimeMessage m = new MimeMessage(Session.getDefaultInstance(new Properties(null)));
        m.setText("CIPS");
        MailImpl mail = new MailImpl("test", new MailAddress("test@test.com"), r, m);
        assertTrue(isSameMimeMessage(m, mail.getMessage()));
        // change the message that should be not referenced by mail that has
        // been disposed, so it should not clone it!
        System.gc();
        Thread.sleep(100);
        mail.getMessage().setSubject("new Subject 2");
        mail.getMessage().setText("new Body 3");
        System.gc();
        Thread.sleep(100);
        assertFalse(isSameMimeMessage(m, mail.getMessage()));
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(m);
    }

    @Test
    public void testMessageDisposing() throws Exception {
        ArrayList<MailAddress> r = new ArrayList<>();
        r.add(new MailAddress("recipient@test.com"));
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                content + sep + body);
        MailImpl mail = new MailImpl("test", new MailAddress("test@test.com"), r, messageFromSources);
        // cloning the message
        MailImpl mailClone = (MailImpl) mail.duplicate();
        LifecycleUtil.dispose(mail);

        assertNotNull(getWrappedMessage(mailClone.getMessage()));
        assertNull(mail.getMessage());

        LifecycleUtil.dispose(mailClone);

        assertNull(mailClone.getMessage());
        assertNull(mail.getMessage());
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
    }

    @Test
    public void testNPE1() throws MessagingException, InterruptedException {
        ArrayList<MailAddress> recipients = new ArrayList<>();
        recipients.add(new MailAddress("recipient@test.com"));
        MimeMessageCopyOnWriteProxy mw = new MimeMessageCopyOnWriteProxy(new MimeMessageInputStreamSource("test",
                new SharedByteArrayInputStream(("Return-path: return@test.com\r\n" + "Content-Transfer-Encoding: plain\r\n" + "Subject: test\r\n\r\n" + "Body Text testNPE1\r\n").
                getBytes())));

        MimeMessageCopyOnWriteProxy mw2 = new MimeMessageCopyOnWriteProxy(mw);
        LifecycleUtil.dispose(mw2);
        mw2 = null;
        System.gc();
        Thread.sleep(1000);
        // the NPE was inside this call
        mw.getMessageSize();
        LifecycleUtil.dispose(mw);
    }

    /**
     * This test throw a NullPointerException when the original message was
     * created by a MimeMessageInputStreamSource.
     */
    @Test
    public void testMessageCloningViaCoW3() throws Exception {
        MimeMessage mmorig = getSimpleMessage();

        MimeMessage mm = new MimeMessageCopyOnWriteProxy(mmorig);

        LifecycleUtil.dispose(mmorig);
        mmorig = null;
        System.gc();
        Thread.sleep(200);

        try {
            mm.writeTo(System.out);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Exception while writing the message to output");
        }

        LifecycleUtil.dispose(mm);
    }

    private static String getReferences(MimeMessage m) {
        StringBuilder ref = new StringBuilder("/");
        while (m instanceof MimeMessageCopyOnWriteProxy) {
            ref.append(((MimeMessageCopyOnWriteProxy) m).refCount.getReferenceCount()).append("/");
            m = ((MimeMessageCopyOnWriteProxy) m).getWrappedMessage();
        }
        if (m instanceof MimeMessageWrapper) {
            ref.append("W");
        } else {
            ref.append("M");
        }
        return ref.toString();
    }

    private static MimeMessage getWrappedMessage(MimeMessage m) {
        while (m instanceof MimeMessageCopyOnWriteProxy) {
            m = ((MimeMessageCopyOnWriteProxy) m).getWrappedMessage();
        }
        return m;
    }

    private static boolean isSameMimeMessage(MimeMessage first, MimeMessage second) {
        return getWrappedMessage(first) == getWrappedMessage(second);

    }
}
