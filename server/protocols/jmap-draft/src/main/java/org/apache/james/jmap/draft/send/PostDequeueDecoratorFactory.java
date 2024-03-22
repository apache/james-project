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

package org.apache.james.jmap.draft.send;

import static org.apache.james.queue.api.MailQueueFactory.SPOOL;

import jakarta.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.james.queue.api.MailQueueItemDecoratorFactory;
import org.apache.james.queue.api.MailQueueName;
import org.apache.james.queue.api.RawMailQueueItem;

public class PostDequeueDecoratorFactory implements MailQueueItemDecoratorFactory {
    private final MailboxManager mailboxManager;
    private final Factory messageIdFactory;
    private final MessageIdManager messageIdManager;
    private final SystemMailboxesProvider systemMailboxesProvider;

    @Inject
    public PostDequeueDecoratorFactory(MailboxManager mailboxManager, Factory messageIdFactory,
            MessageIdManager messageIdManager, SystemMailboxesProvider systemMailboxesProvider) {
        this.mailboxManager = mailboxManager;
        this.messageIdFactory = messageIdFactory;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
    }

    @Override
    public MailQueueItemDecorator decorate(MailQueueItem mailQueueItem, MailQueueName name) {
        if (name.equals(SPOOL)) {
            return new PostDequeueDecorator(mailQueueItem, mailboxManager, messageIdFactory, messageIdManager, systemMailboxesProvider);
        }
        return new RawMailQueueItem(mailQueueItem);
    }

}
