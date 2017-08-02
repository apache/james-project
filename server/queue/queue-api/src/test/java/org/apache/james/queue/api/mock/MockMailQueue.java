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
package org.apache.james.queue.api.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.mail.MessagingException;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailImpl;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockMailQueue implements MailQueue {

    private static final Logger log = LoggerFactory.getLogger(MockMailQueue.class.getName());

    private final LinkedBlockingQueue<Mail> queue = new LinkedBlockingQueue<>();
    private boolean throwException;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    /**
     * Throw an {@link MailQueueException} on next operation
     */
    public void throwExceptionOnNextOperation() {
        this.throwException = true;
    }

    @Override
    public MailQueueItem deQueue() throws MailQueueException {
        if (throwException) {
            throwException = false;
            throw new MailQueueException("Mock");
        }

        try {
            final Mail mail = queue.take();
            return new MailQueueItem() {

                @Override
                public Mail getMail() {
                    return mail;
                }

                @Override
                public void done(boolean success) throws MailQueueException {
                    // do nothing here
                }
            };

        } catch (InterruptedException e) {
            log.error("", e);
            throw new MailQueueException("Mock", e);
        }
    }

    private Mail cloneMail(Mail mail) {
        ByteArrayOutputStream baos = null;
        ByteArrayInputStream bais = null;
        try {
            baos = new ByteArrayOutputStream();
            ((MailImpl) mail).writeMessageTo(baos);
            log.trace("mimemessage stream: >>>" + new String(baos.toByteArray()) + "<<<");
            bais = new ByteArrayInputStream(baos.toByteArray());
            return new MailImpl("MockMailCopy" + new Random().nextLong(),
                    mail.getSender(), mail.getRecipients(), bais);
        } catch (MessagingException ex) {
            log.error("", ex);
            throw new RuntimeException(ex);
        } catch (IOException ex) {
            log.error("", ex);
            throw new RuntimeException(ex);
        } finally {
            IOUtils.closeQuietly(bais);
            IOUtils.closeQuietly(baos);
        }
    }

    @Override
    public void enQueue(final Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        if (throwException) {
            throwException = false;
            throw new MailQueueException("Mock");
        }

        scheduler.schedule(() -> {
            try {
                queue.put(MockMailQueue.this.cloneMail(mail));
            } catch (InterruptedException e) {
                log.error("", e);
                throw new RuntimeException("Mock", e);
            }
        }, delay, unit);
    }

    @Override
    public void enQueue(Mail mail) throws MailQueueException {
        if (throwException) {
            throwException = false;
            throw new MailQueueException("Mock");
        }

        try {
            queue.put(cloneMail(mail));
        } catch (InterruptedException e) {
            log.error("", e);
            throw new MailQueueException("Mock", e);
        }
    }

    public Mail getLastMail() {
        Iterator<Mail> it = queue.iterator();

        Mail mail = null;
        while(it.hasNext()) {
            mail = it.next();
        }

        return mail;
    }

    public void clear() {
        queue.clear();
    }
}
