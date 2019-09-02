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

package org.apache.mailbox.tools.indexer;

import javax.inject.Inject;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.indexer.MessageIdReIndexer;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;
import org.apache.james.task.Task;

public class MessageIdReIndexerImpl implements MessageIdReIndexer {

    private final MailboxManager mailboxManager;
    private final MailboxSessionMapperFactory mailboxSessionMapperFactory;
    private final ListeningMessageSearchIndex index;

    @Inject
    public MessageIdReIndexerImpl(MailboxManager mailboxManager, MailboxSessionMapperFactory mailboxSessionMapperFactory, ListeningMessageSearchIndex index) {
        this.mailboxManager = mailboxManager;
        this.mailboxSessionMapperFactory = mailboxSessionMapperFactory;
        this.index = index;
    }

    @Override
    public Task reIndex(MessageId messageId) {
        return new MessageIdReIndexingTask(mailboxManager, mailboxSessionMapperFactory, index, messageId);
    }
}
