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
package org.apache.james.mailbox.store.search;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

/**
 * {@link ListeningMessageSearchIndex} implementation which wraps another {@link ListeningMessageSearchIndex} and will forward all calls to it.
 * 
 * The only special thing about this is that it will index all the mails in the mailbox on the first call of {@link #search(MailboxSession, Mailbox, SearchQuery)}
 * 
 * This class is mostly useful for in-memory indexes or for indexed that should be recreated on every server restart.
 * 
 *
 * @param <Id>
 */
public class LazyMessageSearchIndex<Id extends MailboxId> extends ListeningMessageSearchIndex<Id> {

    private ListeningMessageSearchIndex<Id> index;
    private final ConcurrentHashMap<Id, Object> indexed = new ConcurrentHashMap<Id, Object>();
    
    
    public LazyMessageSearchIndex(ListeningMessageSearchIndex<Id> index) {
        super(index.getFactory());
        this.index = index;
    }
    
    
    
    @Override
    public void add(MailboxSession session, Mailbox<Id> mailbox, Message<Id> message) throws MailboxException {    
        index.add(session, mailbox, message);
    }

    @Override
    public void delete(MailboxSession session, Mailbox<Id> mailbox, MessageRange range) throws MailboxException {
        index.delete(session, mailbox, range);
    }

    /**
     * Lazy index the mailbox on first search request if it was not indexed before. After indexing is done it delegate the search request to the wrapped
     * {@link MessageSearchIndex}. Be aware that concurrent search requests are blocked on the same "not-yet-indexed" mailbox till it the index process was 
     * complete
     * 
     */
    @Override
    public Iterator<Long> search(final MailboxSession session, final Mailbox<Id> mailbox, SearchQuery searchQuery) throws MailboxException {
        Id id = mailbox.getMailboxId();
        
        Object done = indexed.get(id);
        if (done == null) {
            done = new Object();
            Object oldDone = indexed.putIfAbsent(id, done);
            if (oldDone != null) {
                done = oldDone;
            }
            synchronized (done) {
                Iterator<Message<Id>> messages = getFactory().getMessageMapper(session).findInMailbox(mailbox, MessageRange.all(), FetchType.Full, -1);
                while(messages.hasNext()) {
                    final Message<Id> message = messages.next();
                    try {
                        add(session, mailbox, message);
                    } catch (MailboxException e) {
                        session.getLog().debug("Unable to index message " + message.getUid() + " in mailbox " + mailbox.getName(), e);
                    }

                }
            }
        }
       
        return index.search(session, mailbox, searchQuery);
    }


    @Override
    public void update(MailboxSession session, Mailbox<Id> mailbox, MessageRange range, Flags flags, long modSeq) throws MailboxException {
        index.update(session, mailbox, range, flags, modSeq);
    }

}
