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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;

import com.github.steveash.guavate.Guavate;

public class InMemoryMessageMapper extends AbstractMessageMapper {
    private final Map<InMemoryId, Map<MessageUid, MailboxMessage>> mailboxByUid;
    private static final int INITIAL_SIZE = 256;

    public InMemoryMessageMapper(MailboxSession session, UidProvider uidProvider,
            ModSeqProvider modSeqProvider) {
        super(session, uidProvider, modSeqProvider);
        this.mailboxByUid = new ConcurrentHashMap<>(INITIAL_SIZE);
    }

    private Map<MessageUid, MailboxMessage> getMembershipByUidForMailbox(Mailbox mailbox) {
        return getMembershipByUidForId((InMemoryId) mailbox.getMailboxId());
    }

    private Map<MessageUid, MailboxMessage> getMembershipByUidForId(InMemoryId id) {
        Map<MessageUid, MailboxMessage> membershipByUid = mailboxByUid.get(id);
        if (membershipByUid == null) {
            membershipByUid = new ConcurrentHashMap<>(INITIAL_SIZE);
            mailboxByUid.put(id, membershipByUid);
        }
        return membershipByUid;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) {
        return getMembershipByUidForMailbox(mailbox).size();
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) {
        return getMembershipByUidForMailbox(mailbox).values()
            .stream()
            .filter(member -> !member.isSeen())
            .count();
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) {
        getMembershipByUidForMailbox(mailbox).remove(message.getUid());
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        InMemoryId originalMailboxId = (InMemoryId) original.getMailboxId();
        MessageUid uid = original.getUid();
        MessageMetaData messageMetaData = copy(mailbox, original);
        getMembershipByUidForId(originalMailboxId).remove(uid);
        return messageMetaData;
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType ftype, int max) {
        List<MailboxMessage> results = new ArrayList<>(getMembershipByUidForMailbox(mailbox).values());
        for (Iterator<MailboxMessage> it = results.iterator(); it.hasNext();) {
            if (!set.includes(it.next().getUid())) {
                it.remove();
            }
        }
        
        Collections.sort(results);

        if (max > 0 && results.size() > max) {
            results = results.subList(0, max);
        }
        return results.iterator();
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) {
        return getMembershipByUidForMailbox(mailbox).values()
            .stream()
            .filter(MailboxMessage::isRecent)
            .map(MailboxMessage::getUid)
            .sorted()
            .collect(Guavate.toImmutableList());
    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) {
        List<MailboxMessage> memberships = new ArrayList<>(getMembershipByUidForMailbox(mailbox).values());
        Collections.sort(memberships);
        return memberships.stream()
            .filter(m -> !m.isSeen())
            .findFirst()
            .map(MailboxMessage::getUid)
            .orElse(null);
    }

    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) {
        List<MessageUid> filteredResult = new ArrayList<>();

        Iterator<MailboxMessage> it = findInMailbox(mailbox, messageRange, FetchType.Metadata, UNLIMITED);

        while (it.hasNext()) {
            MailboxMessage member = it.next();
            if (member.isDeleted()) {
                filteredResult.add(member.getUid());
            }
        }
        return filteredResult;
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) {
        return getMembershipByUidForMailbox(mailbox).values()
            .stream()
            .filter(message -> uids.contains(message.getUid()))
            .peek(message -> delete(mailbox, message))
            .collect(Guavate.toImmutableMap(MailboxMessage::getUid, MailboxMessage::metaData));
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) {
        return new ApplicableFlagCalculator(getMembershipByUidForId((InMemoryId) mailbox.getMailboxId()).values())
            .computeApplicableFlags();
    }

    public void deleteAll() {
        mailboxByUid.clear();
    }

    @Override
    public void endRequest() {
        // Do nothing
    }

    @Override
    protected MessageMetaData copy(Mailbox mailbox, MessageUid uid, long modSeq, MailboxMessage original)
            throws MailboxException {
        SimpleMailboxMessage message = SimpleMailboxMessage.copy(mailbox.getMailboxId(), original);
        message.setUid(uid);
        message.setModSeq(modSeq);
        Flags flags = original.createFlags();

        // Mark message as recent as it is a copy
        flags.add(Flag.RECENT);
        message.setFlags(flags);
        return save(mailbox, message);
    }

    @Override
    public MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(mailbox.getMailboxId(), message);
        copy.setUid(message.getUid());
        copy.setModSeq(message.getModSeq());
        getMembershipByUidForMailbox(mailbox).put(message.getUid(), copy);

        return message.metaData();
    }

    @Override
    protected void begin() {

    }

    @Override
    protected void commit() {

    }

    @Override
    protected void rollback() {
    }
}
