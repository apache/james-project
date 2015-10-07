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
package org.apache.james.queue.jms;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.james.core.MailImpl;
import org.apache.james.protocols.smtp.MailAddressException;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueue.MailQueueIterator;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.ConnectionFactory;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Basic JMS test class. Extend this class and start the JMS broker in the super class,
 * Create a queue and implement the getter and setter for the tests to run.
 */
public abstract class AbstractJMSMailQueueTest {

    protected final static String QUEUE_NAME = "test";

    public abstract JMSMailQueue getQueue();

    public abstract void setQueue(JMSMailQueue queue);

    protected ActiveMQConnectionFactory createConnectionFactory() {
        return new ActiveMQConnectionFactory("vm://localhost?create=false");
    }

    protected JMSMailQueue createQueue(ConnectionFactory factory, String queueName) {
        Logger log = LoggerFactory.getLogger(AbstractJMSMailQueueTest.class);
        return new JMSMailQueue(factory, queueName, log);
    }

    @Before
    public void setUp() throws Exception {
        ConnectionFactory connectionFactory = createConnectionFactory();
        setQueue(createQueue(connectionFactory, QUEUE_NAME));
    }

    @Test
    public void testFIFO() throws MessagingException, InterruptedException, IOException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        // should be empty
        assertEquals(0, queue.getSize());

        Mail mail = createMail();
        Mail mail2 = createMail();

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());

        MailQueueItem item = queue.deQueue();
        checkMail(mail, item.getMail());
        item.done(false);

        TimeUnit.MILLISECONDS.sleep(200);

        // ok we should get the same email again
        assertEquals(2, queue.getSize());
        MailQueueItem item2 = queue.deQueue();
        checkMail(mail, item2.getMail());
        item2.done(true);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(1, queue.getSize());
        MailQueueItem item3 = queue.deQueue();
        checkMail(mail2, item3.getMail());
        item3.done(true);

        TimeUnit.MILLISECONDS.sleep(200);

        // should be empty
        assertEquals(0, queue.getSize());
    }

    @Test
    public void testDelayedDeQueue() throws MessagingException, InterruptedException, IOException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        // should be empty
        assertEquals(0, queue.getSize());

        Mail mail = createMail();
        Mail mail2 = createMail();

        long enqueueTime = System.currentTimeMillis();
        queue.enQueue(mail, 3, TimeUnit.SECONDS);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());

        // as we enqueued the mail with delay we should get mail2 first
        MailQueueItem item = queue.deQueue();
        checkMail(mail2, item.getMail());
        item.done(true);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(1, queue.getSize());
        MailQueueItem item2 = queue.deQueue();
        long dequeueTime = System.currentTimeMillis() - enqueueTime;
        checkMail(mail, item2.getMail());
        item2.done(true);
        assertTrue(dequeueTime >= 2000);
        TimeUnit.MILLISECONDS.sleep(200);

        // should be empty
        assertEquals(0, queue.getSize());
    }

    @Test
    public void testFlush() throws MessagingException, InterruptedException, IOException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        // should be empty
        assertEquals(0, queue.getSize());

        final Mail mail = createMail();

        long enqueueTime = System.currentTimeMillis();
        queue.enQueue(mail, 30, TimeUnit.SECONDS);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(1, queue.getSize());

        Thread flushThread = new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    // wait for 2 seconds then flush the queue
                    TimeUnit.MILLISECONDS.sleep(4000);
                    assertEquals(1, queue.flush());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            }
        });
        flushThread.start();

        // this will block until flush is called
        MailQueueItem item = queue.deQueue();
        checkMail(mail, item.getMail());
        item.done(true);

        long dequeueTime = System.currentTimeMillis() - enqueueTime;

        assertEquals(0, queue.getSize());

        // check if the flush kicked in
        assertTrue(dequeueTime < 30 * 1000);
    }

    @Test
    public void testRemoveWithRecipient() throws MessagingException, InterruptedException, MailAddressException {
        final JMSMailQueue queue = getQueue();

        assertEquals(0, queue.getSize());

        Mail mail = createMail();
        mail.setRecipients(Arrays.asList(new MailAddress("remove@me1")));

        Mail mail2 = createMail();
        mail2.setRecipients(Arrays.asList(new MailAddress("remove@me2")));

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());
        assertEquals(1, queue.remove(ManageableMailQueue.Type.Recipient, "remove@me1"));

        TimeUnit.MILLISECONDS.sleep(200);
        assertEquals(1, queue.getSize());

        assertEquals(1, queue.remove(ManageableMailQueue.Type.Recipient, "remove@me2"));
        assertEquals(0, queue.getSize());

    }

    @Test
    public void testRemoveWithSender() throws MessagingException, InterruptedException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        assertEquals(0, queue.getSize());

        MailImpl mail = createMail();
        mail.setSender(new MailAddress("remove@me1"));

        MailImpl mail2 = createMail();
        mail2.setSender(new MailAddress("remove@me2"));

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());
        assertEquals(1, queue.remove(ManageableMailQueue.Type.Sender, "remove@me1"));

        TimeUnit.MILLISECONDS.sleep(200);
        assertEquals(1, queue.getSize());

        assertEquals(1, queue.remove(ManageableMailQueue.Type.Sender, "remove@me2"));
        assertEquals(0, queue.getSize());

    }

    @Test
    public void testRemoveWithName() throws MessagingException, InterruptedException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        assertEquals(0, queue.getSize());

        MailImpl mail = createMail();
        mail.setName("remove@me1");

        MailImpl mail2 = createMail();
        mail2.setName("remove@me2");

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());
        assertEquals(1, queue.remove(ManageableMailQueue.Type.Name, "remove@me1"));

        TimeUnit.MILLISECONDS.sleep(200);
        assertEquals(1, queue.getSize());

        assertEquals(1, queue.remove(ManageableMailQueue.Type.Name, "remove@me2"));
        assertEquals(0, queue.getSize());

    }

    protected MailImpl createMail() throws MessagingException {
        MailImpl mail = new MailImpl();
        mail.setName("" + System.currentTimeMillis());
        mail.setAttribute("test1", System.currentTimeMillis());
        mail.setErrorMessage(UUID.randomUUID().toString());
        mail.setLastUpdated(new Date());
        mail.setRecipients(Arrays.asList(new MailAddress("test@test"), new MailAddress("test@test2")));
        mail.setSender(new MailAddress("sender@senderdomain"));

        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setText("test");
        message.setHeader("testheader", "testvalie");
        message.saveChanges();
        mail.setMessage(message);
        return mail;

    }

    @SuppressWarnings("unchecked")
    protected void checkMail(Mail enqueuedMail, Mail dequeuedMail) throws MessagingException, IOException {
        assertEquals(enqueuedMail.getErrorMessage(), dequeuedMail.getErrorMessage());
        assertEquals(enqueuedMail.getMessageSize(), dequeuedMail.getMessageSize());
        assertEquals(enqueuedMail.getName(), dequeuedMail.getName());
        assertEquals(enqueuedMail.getRemoteAddr(), dequeuedMail.getRemoteAddr());
        assertEquals(enqueuedMail.getState(), dequeuedMail.getState());
        assertEquals(enqueuedMail.getLastUpdated(), dequeuedMail.getLastUpdated());
        assertEquals(enqueuedMail.getRemoteHost(), dequeuedMail.getRemoteHost());
        assertEquals(enqueuedMail.getSender(), dequeuedMail.getSender());

        assertEquals(enqueuedMail.getRecipients().size(), dequeuedMail.getRecipients().size());
        Iterator<String> attributes = enqueuedMail.getAttributeNames();
        while (attributes.hasNext()) {
            String name = attributes.next();
            assertNotNull(dequeuedMail.getAttribute(name));
        }

        MimeMessage enqueuedMsg = enqueuedMail.getMessage();
        MimeMessage dequeuedMsg = dequeuedMail.getMessage();
        Enumeration<String> enQueuedHeaders = enqueuedMsg.getAllHeaderLines();
        Enumeration<String> deQueuedHeaders = dequeuedMsg.getAllHeaderLines();
        while (enQueuedHeaders.hasMoreElements()) {
            assertEquals(enQueuedHeaders.nextElement(), deQueuedHeaders.nextElement());

        }
        assertFalse(deQueuedHeaders.hasMoreElements());

        assertEquals(enqueuedMsg.getContent(), dequeuedMsg.getContent());

    }

    @Test
    public void testPrioritySupport() throws InterruptedException, MessagingException, IOException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        // should be empty
        assertEquals(0, queue.getSize());

        Mail mail = createMail();
        Mail mail2 = createMail();
        mail2.setAttribute(JMSMailQueue.MAIL_PRIORITY, JMSMailQueue.HIGH_PRIORITY);

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());

        // we should get mail2 first as it has a higher priority set
        assertEquals(2, queue.getSize());
        MailQueueItem item2 = queue.deQueue();
        checkMail(mail2, item2.getMail());
        item2.done(true);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(1, queue.getSize());
        MailQueueItem item3 = queue.deQueue();
        checkMail(mail, item3.getMail());
        item3.done(true);

        TimeUnit.MILLISECONDS.sleep(200);

        // should be empty
        assertEquals(0, queue.getSize());
    }

    @Test
    public void testBrowse() throws MessagingException, InterruptedException, IOException, MailAddressException {
        final JMSMailQueue queue = getQueue();
        // should be empty
        assertEquals(0, queue.getSize());

        Mail mail = createMail();
        Mail mail2 = createMail();

        queue.enQueue(mail);
        queue.enQueue(mail2);

        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(2, queue.getSize());

        MailQueueIterator it = queue.browse();
        checkMail(mail, it.next().getMail());
        checkMail(mail2, it.next().getMail());
        assertFalse(it.hasNext());
        it.close();

        assertEquals(2, queue.getSize());
        MailQueueItem item2 = queue.deQueue();
        checkMail(mail, item2.getMail());
        item2.done(true);
        TimeUnit.MILLISECONDS.sleep(200);

        assertEquals(1, queue.getSize());
        it = queue.browse();
        checkMail(mail2, it.next().getMail());
        assertFalse(it.hasNext());
        it.close();
    }
}
