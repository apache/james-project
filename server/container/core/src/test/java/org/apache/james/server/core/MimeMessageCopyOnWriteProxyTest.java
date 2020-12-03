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
import static org.assertj.core.api.Fail.fail;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class MimeMessageCopyOnWriteProxyTest extends MimeMessageFromStreamTest {

    final String content = "Subject: foo\r\nContent-Transfer-Encoding2: plain";
    final String sep = "\r\n\r\n";
    final String body = "bar\r\n.\r\n";
    final String mimeMessageAsString = content + sep + body;

    @BeforeAll
    static void setUp() {
        ContentTypeCleaner.initialize();
    }

    @Override
    protected MimeMessage getMessageFromSources(String sources) throws Exception {
        MimeMessageInputStreamSource mmis = new MimeMessageInputStreamSource("test", new SharedByteArrayInputStream(sources.getBytes()));
        return new MimeMessageCopyOnWriteProxy(mmis);
    }

    @Test
    void testMessageCloning1() throws Exception {
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                mimeMessageAsString);
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(messageFromSources)
            .build();
        MailImpl m2 = MailImpl.duplicate(mail);
        assertThat(mail).isNotSameAs((m2));
        assertThat(mail.getMessage()).isNotSameAs(m2.getMessage());
        // test that the wrapped message is the same
        assertThat(isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        // test it is the same after read only operations!
        mail.getMessage().getSubject();
        assertThat(isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        mail.getMessage().setText("new body");
        mail.getMessage().saveChanges();
        // test it is different after a write operation!
        mail.getMessage().setSubject("new Subject");
        assertThat(!isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(m2);
        LifecycleUtil.dispose(messageFromSources);
    }

    @Test
    void testMessageCloning2() throws Exception {
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                mimeMessageAsString);
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(messageFromSources)
            .build();

        MailImpl m2 = MailImpl.duplicate(mail);
        assertThat(mail).isNotSameAs((m2));
        assertThat(mail.getMessage()).isNotSameAs(m2.getMessage());
        // test that the wrapped message is the same
        assertThat(isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        // test it is the same after real only operations!
        m2.getMessage().getSubject();
        assertThat(isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        m2.getMessage().setText("new body");
        m2.getMessage().saveChanges();
        // test it is different after a write operation!
        m2.getMessage().setSubject("new Subject");
        assertThat(!isSameMimeMessage(m2.getMessage(), mail.getMessage())).isTrue();
        // check that the subjects are correct on both mails!
        assertThat("new Subject").isEqualTo(m2.getMessage().getSubject());
        assertThat("foo").isEqualTo(mail.getMessage().getSubject());
        // cloning again the messages
        Mail m2clone = MailImpl.duplicate(m2);
        assertThat(isSameMimeMessage(m2clone.getMessage(), m2.getMessage())).isTrue();
        MimeMessage mm = getWrappedMessage(m2.getMessage());
        assertThat(m2clone.getMessage()).isNotSameAs(m2.getMessage());
        // test that m2clone has a valid wrapped message
        MimeMessage mm3 = getWrappedMessage(m2clone.getMessage());
        assertThat(mm3).isNotNull();
        // dispose m2 and check that the clone has still a valid message and it
        // is the same!
        LifecycleUtil.dispose(m2);
        assertThat(getWrappedMessage(m2clone.getMessage())).isEqualTo(mm3);
        // change the message that should be not referenced by m2 that has
        // been disposed, so it should not clone it!
        m2clone.getMessage().setSubject("new Subject 2");
        m2clone.getMessage().setText("new Body 3");
        assertThat(isSameMimeMessage(m2clone.getMessage(), mm)).isTrue();
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
    }

    /**
     * If I create a new MimeMessageCopyOnWriteProxy from another
     * MimeMessageCopyOnWriteProxy, I remove references to the first and I
     * change the second, then it should not clone
     */
    @Test
    void testMessageAvoidCloning() throws Exception {
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                mimeMessageAsString);
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(messageFromSources)
            .build();
        // cloning the message
        Mail mailClone = MailImpl.duplicate(mail);
        assertThat(isSameMimeMessage(mailClone.getMessage(), mail.getMessage())).isTrue();
        MimeMessage mm = getWrappedMessage(mail.getMessage());
        assertThat(mailClone.getMessage()).isNotSameAs(mail.getMessage());
        // dispose mail and check that the clone has still a valid message and
        // it is the same!
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
        // need to add a gc and a wait, because the original mimemessage should
        // be finalized before the test.
        System.gc();
        Thread.sleep(1000);
        // dumb test
        assertThat(isSameMimeMessage(mailClone.getMessage(), mailClone.getMessage())).isTrue();
        // change the message that should be not referenced by mail that has
        // been disposed, so it should not clone it!
        mailClone.getMessage().setSubject("new Subject 2");
        mailClone.getMessage().setText("new Body 3");
        assertThat(isSameMimeMessage(mailClone.getMessage(), mm)).isTrue();
        LifecycleUtil.dispose(mailClone);
        LifecycleUtil.dispose(mm);
    }

    /**
     * If I create a new MimeMessageCopyOnWriteProxy from a MimeMessage and I
     * change the new message, the original should be unaltered and the proxy
     * should clone the message.
     */
    @Test
    void testMessageCloning3() throws Exception {
        MimeMessage mimeMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setText("CIPS")
            .build();
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(mimeMessage)
            .build();

        assertThat(isSameMimeMessage(mimeMessage, mail.getMessage())).isTrue();
        // change the message that should be not referenced by mail that has
        // been disposed, so it should not clone it!
        System.gc();
        Thread.sleep(100);
        mail.getMessage().setSubject("new Subject 2");
        mail.getMessage().setText("new Body 3");
        System.gc();
        Thread.sleep(100);
        assertThat(isSameMimeMessage(mimeMessage, mail.getMessage())).isFalse();
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(mimeMessage);
    }

    @Test
    void testMessageDisposing() throws Exception {
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                mimeMessageAsString);
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(messageFromSources)
            .build();
        // cloning the message
        MailImpl mailClone = MailImpl.duplicate(mail);
        LifecycleUtil.dispose(mail);

        assertThat(getWrappedMessage(mailClone.getMessage())).isNotNull();
        assertThat(mail.getMessage()).isNull();

        LifecycleUtil.dispose(mailClone);

        assertThat(mailClone.getMessage()).isNull();
        assertThat(mail.getMessage()).isNull();
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
    }

    @Test
    void testNPE1() throws MessagingException, InterruptedException {
        MimeMessageCopyOnWriteProxy mw = new MimeMessageCopyOnWriteProxy(new MimeMessageInputStreamSource("test",
                new SharedByteArrayInputStream(("Return-path: return@test.com\r\n" + "Content-Transfer-Encoding: plain\r\n" + "Subject: test\r\n\r\n" + "Body Text testNPE1\r\n")
                        .getBytes())));

        MimeMessageCopyOnWriteProxy mw2 = new MimeMessageCopyOnWriteProxy(mw);
        LifecycleUtil.dispose(mw2);
        mw2 = null;
        System.gc();
        Thread.sleep(1000);
        // the NPE was inside this call
        mw.getMessageSize();
        LifecycleUtil.dispose(mw);
    }

    @Disabled("JAMES-3477 MimeMessageCopyOnWriteProxy is not thread safe")
    @Test
    void testNPE2() throws MessagingException, InterruptedException, ExecutionException {
        MimeMessageCopyOnWriteProxy mw = new MimeMessageCopyOnWriteProxy(new MimeMessageInputStreamSource("test",
                new SharedByteArrayInputStream(("Return-path: return@test.com\r\n" + "Content-Transfer-Encoding: plain\r\n" + "Subject: test\r\n\r\n" + "Body Text testNPE1\r\n")
                        .getBytes())));

        ConcurrentTestRunner
                .builder()
                .operation((threadNumber, step) -> {
                    switch (step % 3) {
                        case 0:
                            mw.setSubject(String.valueOf(threadNumber) + "-" + step);
                            break;
                        case 1:
                            MimeMessageCopyOnWriteProxy mw2 = new MimeMessageCopyOnWriteProxy(mw);
                            mw2.setSubject(String.valueOf(threadNumber) + "-" + step);
                            LifecycleUtil.dispose(mw2);
                            break;
                        case 2:
                            mw.getSubject();
                            break;
                    }
                })
                .threadCount(8)
                .operationCount(1000)
                .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    /**
     * This test throw a NullPointerException when the original message was
     * created by a MimeMessageInputStreamSource.
     */
    @Test
    void testMessageCloningViaCoW3() throws Exception {
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

    @Test
    void testMessageWithWrongContentTypeShouldNotThrow() throws Exception {
        MimeMessageCopyOnWriteProxy messageFromSources = (MimeMessageCopyOnWriteProxy) getMessageFromSources(
                mimeMessageAsString);
        MailImpl mail = MailImpl.builder()
            .name("test")
            .sender("test@test.com")
            .addRecipient("recipient@test.com")
            .mimeMessage(messageFromSources)
            .build();
        mail.getMessage().addHeader("Content-Type", "file;name=\"malformed.pdf\"");
        mail.getMessage().saveChanges();
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(messageFromSources);
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
