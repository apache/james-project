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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.james.core.Username;
import org.apache.james.mailbox.events.Event;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.mailbox.events.MailboxListener;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.concurrent.NamedThreadFactory;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public interface MailboxManagerStressContract<T extends MailboxManager> {

    int APPEND_OPERATIONS = 200;

    T getManager();

    EventBus retrieveEventBus();

    @Test
    default void testStressTest() throws InterruptedException, MailboxException {
        ThreadFactory threadFactory = NamedThreadFactory.withClassName(getClass());

        CountDownLatch latch = new CountDownLatch(APPEND_OPERATIONS);
        ExecutorService pool = Executors.newFixedThreadPool(APPEND_OPERATIONS / 20, threadFactory);
        Collection<MessageUid> uList = new ConcurrentLinkedDeque<>();
        Username username = Username.of("username");
        MailboxSession session = getManager().createSystemSession(username);
        getManager().startProcessingRequest(session);
        MailboxPath path = MailboxPath.forUser(username, "INBOX");
        MailboxId mailboxId = getManager().createMailbox(path, session).get();
        retrieveEventBus().register(new MailboxListener() {
            @Override
            public void event(Event event) {
                MessageUid u = ((MailboxListener.Added) event).getUids().iterator().next();
                uList.add(u);
            }
        }, new MailboxIdRegistrationKey(mailboxId));
        getManager().endProcessingRequest(session);
        getManager().logout(session);

        final AtomicBoolean fail = new AtomicBoolean(false);
        final ConcurrentHashMap<MessageUid, Object> uids = new ConcurrentHashMap<>();

        // fire of append operations
        for (int i = 0; i < APPEND_OPERATIONS; i++) {
            pool.execute(() -> {
                if (fail.get()) {
                    latch.countDown();
                    return;
                }

                try {
                    MailboxSession mailboxSession = getManager().createSystemSession(username);

                    getManager().startProcessingRequest(mailboxSession);
                    MessageManager m = getManager().getMailbox(path, mailboxSession);
                    ComposedMessageId messageId = m.appendMessage(
                        MessageManager.AppendCommand
                            .from(Message.Builder.of()
                                .setSubject("test")
                                .setBody("testmail", StandardCharsets.UTF_8)), mailboxSession).getId();

                    System.out.println("Append message with uid=" + messageId.getUid());
                    if (uids.put(messageId.getUid(), new Object()) != null) {
                        fail.set(true);
                    }
                    getManager().endProcessingRequest(mailboxSession);
                    getManager().logout(mailboxSession);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail.set(true);
                } finally {
                    latch.countDown();
                }


            });
        }

        latch.await(10L, TimeUnit.MINUTES);

        // check if there is no duplicates
        // For mailboxes without locks, even if the UID is monotic, as re-scheduling can happen between UID generation and event delivery,
        // we can not check the order on the event listener
        // No UID duplicates prevents message loss
        assertThat(ImmutableSet.copyOf(uList).size()).isEqualTo(APPEND_OPERATIONS);
        assertThat(fail.get()).describedAs("Unable to append all messages").isFalse();
        pool.shutdown();
    }
}
