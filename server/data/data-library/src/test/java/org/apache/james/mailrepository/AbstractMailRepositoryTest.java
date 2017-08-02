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
package org.apache.james.mailrepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.core.MimeMessageInputStreamSource;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailrepository.api.MailRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractMailRepositoryTest {

    final String content = "Subject: test\r\nAAAContent-Transfer-Encoding: text/plain";
    final String sep = "\r\n\r\n";
    final String body = "original body\r\n.\r\n";
    protected Mail mail;
    protected MailRepository mailRepository;
    protected MimeMessage mimeMessage;

    public AbstractMailRepositoryTest() {
        super();
    }

    @Before
    public void setUp() throws Exception {
        mailRepository = getMailRepository();
        MimeMessageInputStreamSource mmis = new MimeMessageInputStreamSource("test",
            new SharedByteArrayInputStream((content + sep + body).getBytes()));
        mimeMessage = new MimeMessageCopyOnWriteProxy(mmis);
        Collection<MailAddress> recipients = new ArrayList<>();
        recipients.add(new MailAddress("rec1", "domain.com"));
        recipients.add(new MailAddress("rec2", "domain.com"));
        mail = new MailImpl("mail1", new MailAddress("sender", "domain.com"), recipients, mimeMessage);
        mail.setAttribute("testAttribute", "testValue");
    }

    protected abstract MailRepository getMailRepository() throws Exception;

    @After
    public void tearDown() throws Exception {
        for (Iterator<String> i = mailRepository.list(); i.hasNext();) {
            mailRepository.remove(i.next());
        }
        LifecycleUtil.dispose(mail);
        LifecycleUtil.dispose(mimeMessage);
        LifecycleUtil.dispose(mailRepository);
    }

    @Test
    public void testStoreAndRetrieveMail() throws MessagingException, IOException {
        try {
            mailRepository.store(mail);
        } catch (Exception e) {
            fail("Failed to store mail");
        }
        Mail m2 = mailRepository.retrieve(mailRepository.list().next());

        assertEquals("stored and retrieved messages do not match", mail.getMessage().getContent().toString(), m2.
                getMessage().getContent().toString());
        assertEquals("stored and retrieved message sizes do not match", mail.getMessageSize(), m2.getMessageSize());
        assertEquals("stored and retrieved keys do not match", mail.getName(), m2.getName());
        assertEquals("stored and retrieved states do not match", mail.getState(), m2.getState());
        assertEquals("stored and retrieved attributes do not match", mail.getAttribute("testAttribute"),
                m2.getAttribute("testAttribute"));
        LifecycleUtil.dispose(m2);
    }

    @Test
    public void testEmptyRepository() throws MessagingException {
        assertFalse(mailRepository.list().hasNext());
        // locking does not check for the existence of the file
        // assertFalse(mailRepository.lock("random"));
        assertNull(mailRepository.retrieve("random"));
        // removing an unexisting message does not throw/return errors
        mailRepository.remove("random");
    }

    @Test
    public void testListMail() throws MessagingException {
        mailRepository.store(mail);
        mailRepository.store(mail);
        Iterator<String> i = mailRepository.list();
        assertTrue(i.hasNext());
        assertEquals(mail.getName(), i.next());
        assertFalse("Found more than one message after storing 2 times the SAME message: it MUST update the previous",
                i.hasNext());
    }

    /**
     * This test has been written as a proof to:
     * http://issues.apache.org/jira/browse/JAMES-559
     */
    @Test
    public void testJames559() throws Exception {
        mailRepository.store(mail);

        Mail m2 = mailRepository.retrieve("mail1");
        m2.getMessage().setHeader("X-Header", "foobar");
        m2.getMessage().saveChanges();

        mailRepository.store(m2);
        // ALWAYS remember to dispose mails!
        LifecycleUtil.dispose(m2);

        m2 = mailRepository.retrieve("mail1");
        assertEquals(mail.getMessage().getContent().toString(), m2.getMessage().getContent().toString());

        LifecycleUtil.dispose(mail);
        mail = null;
        LifecycleUtil.dispose(m2);

        mailRepository.remove("mail1");
    }

    /**
     * This test has been written as a proof to:
     * http://issues.apache.org/jira/browse/JAMES-559
     */
    @Test
    public void testJames559WithoutSaveChanges() throws Exception {
        mailRepository.store(mail);

        Mail m2 = mailRepository.retrieve("mail1");
        m2.getMessage().setHeader("X-Header", "foobar");

        mailRepository.store(m2);
        // ALWAYS remember to dispose mails!
        LifecycleUtil.dispose(m2);

        m2 = mailRepository.retrieve("mail1");
        assertEquals(mail.getMessage().getContent().toString(), m2.getMessage().getContent().toString());

        LifecycleUtil.dispose(mail);
        mail = null;

        LifecycleUtil.dispose(m2);

        mailRepository.remove("mail1");
    }
}
