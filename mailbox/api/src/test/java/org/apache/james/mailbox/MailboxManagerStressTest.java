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
package org.apache.james.mailbox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Test;

import com.google.common.collect.ImmutableSet;

public abstract class MailboxManagerStressTest {

    private final static int APPEND_OPERATIONS = 200;

    private MailboxManager mailboxManager;

    protected abstract MailboxManager provideManager() throws MailboxException;

    
    public void setUp() throws Exception {
        this.mailboxManager = provideManager();
    }

    @Test
    public void testStressTest() throws InterruptedException, MailboxException {

        final CountDownLatch latch = new CountDownLatch(APPEND_OPERATIONS);
        final ExecutorService pool = Executors.newFixedThreadPool(APPEND_OPERATIONS / 2);
        final List<MessageUid> uList = new ArrayList<>();
        final String username = "username";
        MailboxSession session = mailboxManager.createSystemSession(username);
        mailboxManager.startProcessingRequest(session);
        final MailboxPath path = MailboxPath.forUser(username, "INBOX");
        mailboxManager.createMailbox(path, session);
        mailboxManager.addListener(path, new MailboxListener() {

            @Override
            public ListenerType getType() {
                return ListenerType.MAILBOX;
            }

            @Override
            public ExecutionMode getExecutionMode() {
                return ExecutionMode.SYNCHRONOUS;
            }

            @Override
            public void event(Event event) {
                MessageUid u = ((Added) event).getUids().get(0);
                uList.add(u);
            }
        }, session);
        mailboxManager.endProcessingRequest(session);
        mailboxManager.logout(session, false);

        final AtomicBoolean fail = new AtomicBoolean(false);
        final ConcurrentHashMap<MessageUid, Object> uids = new ConcurrentHashMap<>();

        // fire of 1000 append operations
        for (int i = 0; i < APPEND_OPERATIONS; i++) {
            pool.execute(() -> {
                if (fail.get()) {
                    latch.countDown();
                    return;
                }


                try {
                    MailboxSession mailboxSession = mailboxManager.createSystemSession(username);

                    mailboxManager.startProcessingRequest(mailboxSession);
                    MessageManager m = mailboxManager.getMailbox(path, mailboxSession);
                    ComposedMessageId messageId = m.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), mailboxSession, false, new Flags());

                    System.out.println("Append message with uid=" + messageId.getUid());
                    if (uids.put(messageId.getUid(), new Object()) != null) {
                        fail.set(true);
                    }
                    mailboxManager.endProcessingRequest(mailboxSession);
                    mailboxManager.logout(mailboxSession, false);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail.set(true);
                } finally {
                    latch.countDown();
                }


            });
        }

        latch.await();

        // check if there is no duplicates
        // For mailboxes without locks, even if the UID is monotic, as re-scheduling can happen between UID generation and event delivery,
        // we can not check the order on the event listener
        // No UID duplicates prevents message loss
        assertEquals(APPEND_OPERATIONS, ImmutableSet.copyOf(uList).size());
        assertFalse("Unable to append all messages", fail.get());
        pool.shutdown();
    }
}
