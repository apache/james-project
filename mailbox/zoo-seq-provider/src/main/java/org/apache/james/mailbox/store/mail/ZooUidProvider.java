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
package org.apache.james.mailbox.store.mail;

import com.google.common.base.Preconditions;
import com.netflix.curator.RetryPolicy;
import com.netflix.curator.framework.CuratorFramework;
import com.netflix.curator.framework.imps.CuratorFrameworkState;
import com.netflix.curator.framework.recipes.atomic.AtomicValue;
import com.netflix.curator.framework.recipes.atomic.DistributedAtomicLong;
import com.netflix.curator.retry.RetryOneTime;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * ZooKeeper based implementation of a distributed sequential UID generator.
 */
public class ZooUidProvider<E extends MailboxId> implements UidProvider<E> {
    // TODO: use ZK paths to store uid and modSeq, etc.

    public static final String UID_PATH_SUFFIX = "-uid";
    private final CuratorFramework client;
    private final RetryPolicy retryPolicy;

    public ZooUidProvider(CuratorFramework client) {
        this(client, new RetryOneTime(1));
    }

    public ZooUidProvider(CuratorFramework client, RetryPolicy retryPolicy) {
        Preconditions.checkNotNull(client, "Curator client is null");
        Preconditions.checkNotNull(retryPolicy, "Retry policy is null");
        this.client = client;
        this.retryPolicy = retryPolicy;
    }

    @Override
    public long nextUid(MailboxSession session, Mailbox<E> mailbox) throws MailboxException {
        if (client.getState() == CuratorFrameworkState.STARTED) {
            DistributedAtomicLong uid = new DistributedAtomicLong(client, pathForMailbox(mailbox), retryPolicy);
            try {
                uid.increment();
                AtomicValue<Long> value = uid.get();
                if (value.succeeded()) {
                    return value.postValue();
                }
            } catch (Exception e) {
                throw new MailboxException("Exception incrementing UID for session " + session, e);
            }
        }
        throw new MailboxException("Curator client is closed.");
    }

    @Override
    public long lastUid(MailboxSession session, Mailbox<E> mailbox) throws MailboxException {
        if (client.getState() == CuratorFrameworkState.STARTED) {
            DistributedAtomicLong uid = new DistributedAtomicLong(client, pathForMailbox(mailbox), retryPolicy);
            try {
                AtomicValue<Long> value = uid.get();
                if (value.succeeded()) {
                    return value.postValue();
                }
            } catch (Exception e) {
                throw new MailboxException("Exception getting last UID for session " + session, e);
            }
        }
        throw new MailboxException("Curator client is closed.");
    }

    public static <E extends MailboxId> String pathForMailbox(Mailbox<E> mailbox) {
        return mailbox.getMailboxId().serialize() + UID_PATH_SUFFIX;
    }
}
