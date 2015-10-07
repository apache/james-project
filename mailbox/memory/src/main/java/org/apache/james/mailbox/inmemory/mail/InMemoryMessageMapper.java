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

package org.apache.james.mailbox.inmemory.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;

public class InMemoryMessageMapper extends AbstractMessageMapper<InMemoryId> {

    private Map<InMemoryId, Map<Long, Message<InMemoryId>>> mailboxByUid;
    private static final int INITIAL_SIZE = 256;

    public InMemoryMessageMapper(MailboxSession session, UidProvider<InMemoryId> uidProvider,
            ModSeqProvider<InMemoryId> modSeqProvider) {
        super(session, uidProvider, modSeqProvider);
        this.mailboxByUid = new ConcurrentHashMap<InMemoryId, Map<Long, Message<InMemoryId>>>(INITIAL_SIZE);
    }

    private Map<Long, Message<InMemoryId>> getMembershipByUidForMailbox(Mailbox<InMemoryId> mailbox) {
        Map<Long, Message<InMemoryId>> membershipByUid = mailboxByUid.get(mailbox.getMailboxId());
        if (membershipByUid == null) {
            membershipByUid = new ConcurrentHashMap<Long, Message<InMemoryId>>(INITIAL_SIZE);
            mailboxByUid.put(mailbox.getMailboxId(), membershipByUid);
        }
        return membershipByUid;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countMessagesInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public long countMessagesInMailbox(Mailbox<InMemoryId> mailbox) throws MailboxException {
        return getMembershipByUidForMailbox(mailbox).size();
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countUnseenMessagesInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public long countUnseenMessagesInMailbox(Mailbox<InMemoryId> mailbox) throws MailboxException {
        long count = 0;
        for (Message<InMemoryId> member : getMembershipByUidForMailbox(mailbox).values()) {
            if (!member.isSeen()) {
                count++;
            }
        }
        return count;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    public void delete(Mailbox<InMemoryId> mailbox, Message<InMemoryId> message) throws MailboxException {
        getMembershipByUidForMailbox(mailbox).remove(message.getUid());
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.store.mail.MessageMapper.FetchType, int)
     */
    public Iterator<Message<InMemoryId>> findInMailbox(Mailbox<InMemoryId> mailbox, MessageRange set, FetchType ftype, int max)
            throws MailboxException {
        List<Message<InMemoryId>> results;
        final MessageRange.Type type = set.getType();
        switch (type) {
        case ALL:
            results = new ArrayList<Message<InMemoryId>>(getMembershipByUidForMailbox(mailbox).values());
            break;
        case FROM:
            results = new ArrayList<Message<InMemoryId>>(getMembershipByUidForMailbox(mailbox).values());
            for (final Iterator<Message<InMemoryId>> it = results.iterator(); it.hasNext();) {
                if (it.next().getUid() < set.getUidFrom()) {
                    it.remove();
                }
            }
            break;
        case RANGE:
            results = new ArrayList<Message<InMemoryId>>(getMembershipByUidForMailbox(mailbox).values());
            for (final Iterator<Message<InMemoryId>> it = results.iterator(); it.hasNext();) {
                final long uid = it.next().getUid();
                if (uid < set.getUidFrom() || uid > set.getUidTo()) {
                    it.remove();
                }
            }
            break;
        case ONE:
            results = new ArrayList<Message<InMemoryId>>(1);
            final Message<InMemoryId> member = getMembershipByUidForMailbox(mailbox).get(set.getUidFrom());
            if (member != null) {
                results.add(member);
            }
            break;
        default:
            results = new ArrayList<Message<InMemoryId>>();
            break;
        }
        Collections.sort(results);

        if (max > 0 && results.size() > max) {
            results = results.subList(0, max - 1);
        }
        return results.iterator();
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findRecentMessageUidsInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<InMemoryId> mailbox) throws MailboxException {
        final List<Long> results = new ArrayList<Long>();
        for (Message<InMemoryId> member : getMembershipByUidForMailbox(mailbox).values()) {
            if (member.isRecent()) {
                results.add(member.getUid());
            }
        }
        Collections.sort(results);

        return results;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findFirstUnseenMessageUid(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    public Long findFirstUnseenMessageUid(Mailbox<InMemoryId> mailbox) throws MailboxException {
        List<Message<InMemoryId>> memberships = new ArrayList<Message<InMemoryId>>(getMembershipByUidForMailbox(mailbox).values());
        Collections.sort(memberships);
        for (int i = 0; i < memberships.size(); i++) {
            Message<InMemoryId> m = memberships.get(i);
            if (m.isSeen() == false) {
                return m.getUid();
            }
        }
        return null;
    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(final Mailbox<InMemoryId> mailbox, MessageRange set)
            throws MailboxException {
        final Map<Long, MessageMetaData> filteredResult = new HashMap<Long, MessageMetaData>();

        Iterator<Message<InMemoryId>> it = findInMailbox(mailbox, set, FetchType.Metadata, -1);
        while (it.hasNext()) {
            Message<InMemoryId> member = it.next();
            if (member.isDeleted()) {
                filteredResult.put(member.getUid(), new SimpleMessageMetaData(member));

                delete(mailbox, member);
            }
        }
        return filteredResult;
    }

    public void deleteAll() {
        mailboxByUid.clear();
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MessageMapper#move(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData move(Mailbox<InMemoryId> mailbox, Message<InMemoryId> original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    /**
     * Do nothing
     */
    public void endRequest() {
        // Do nothing
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#copy(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      long, long, org.apache.james.mailbox.store.mail.model.Message)
     */
    protected MessageMetaData copy(Mailbox<InMemoryId> mailbox, long uid, long modSeq, Message<InMemoryId> original)
            throws MailboxException {
        SimpleMessage<InMemoryId> message = new SimpleMessage<InMemoryId>(mailbox, original);
        message.setUid(uid);
        message.setModSeq(modSeq);
        Flags flags = original.createFlags();

        // Mark message as recent as it is a copy
        flags.add(Flag.RECENT);
        message.setFlags(flags);
        return save(mailbox, message);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#save(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    protected MessageMetaData save(Mailbox<InMemoryId> mailbox, Message<InMemoryId> message) throws MailboxException {
        SimpleMessage<InMemoryId> copy = new SimpleMessage<InMemoryId>(mailbox, message);
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        getMembershipByUidForMailbox(mailbox).put(message.getUid(), copy);

        return new SimpleMessageMetaData(message);
    }

    /**
     * Do nothing
     */
    protected void begin() throws MailboxException {

    }

    /**
     * Do nothing
     */
    protected void commit() throws MailboxException {

    }

    /**
     * Do nothing
     */
    protected void rollback() throws MailboxException {
    }
}
