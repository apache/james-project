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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

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
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Test;
import org.slf4j.LoggerFactory;

public abstract class AbstractStressTest {

    private final static int APPEND_OPERATIONS = 200;


    protected abstract MailboxManager getMailboxManager();

    @Test
    public void testStessTest() throws InterruptedException, MailboxException {

        final CountDownLatch latch = new CountDownLatch(APPEND_OPERATIONS);
        final ExecutorService pool = Executors.newFixedThreadPool(APPEND_OPERATIONS / 2);
        final List<Long> uList = new ArrayList<Long>();
        final String username = "username";
        MailboxSession session = getMailboxManager().createSystemSession(username, LoggerFactory.getLogger("Test"));
        getMailboxManager().startProcessingRequest(session);
        final MailboxPath path = new MailboxPath(MailboxConstants.USER_NAMESPACE, username, "INBOX");
        getMailboxManager().createMailbox(path, session);
        getMailboxManager().addListener(path, new MailboxListener() {


            @Override
            public void event(Event event) {
                long u = ((Added) event).getUids().get(0);
                uList.add(u);
            }
        }, session);
        getMailboxManager().endProcessingRequest(session);
        getMailboxManager().logout(session, false);

        final AtomicBoolean fail = new AtomicBoolean(false);
        final ConcurrentHashMap<Long, Object> uids = new ConcurrentHashMap<Long, Object>();

        // fire of 1000 append operations
        for (int i = 0; i < APPEND_OPERATIONS; i++) {
            pool.execute(new Runnable() {

                public void run() {
                    if (fail.get()) {
                        latch.countDown();
                        return;
                    }


                    try {
                        MailboxSession session = getMailboxManager().createSystemSession(username, LoggerFactory.getLogger("Test"));

                        getMailboxManager().startProcessingRequest(session);
                        MessageManager m = getMailboxManager().getMailbox(path, session);
                        Long uid = m.appendMessage(new ByteArrayInputStream("Subject: test\r\n\r\ntestmail".getBytes()), new Date(), session, false, new Flags());

                        System.out.println("Append message with uid=" + uid);
                        if (uids.put(uid, new Object()) != null) {
                            fail.set(true);
                        }
                        getMailboxManager().endProcessingRequest(session);
                        getMailboxManager().logout(session, false);
                    } catch (MailboxException e) {
                        e.printStackTrace();
                        fail.set(true);
                    } finally {
                        latch.countDown();
                    }


                }
            });
        }

        latch.await();

        // check if the uids were higher on each append. See MAILBOX-131
        long last = 0;
        for (int i = 0; i < uList.size(); i++) {
            long l = uList.get(i);
            if (l <= last) {
                fail(l + "->" + last);
            } else {
                last = l;
            }

        }
        assertFalse("Unable to append all messages", fail.get());
        pool.shutdown();
    }
}
