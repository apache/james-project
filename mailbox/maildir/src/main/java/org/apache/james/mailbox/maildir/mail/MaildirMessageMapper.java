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
package org.apache.james.mailbox.maildir.mail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.commons.io.FileUtils;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.maildir.MaildirFolder;
import org.apache.james.mailbox.maildir.MaildirMessageName;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.maildir.mail.model.MaildirMailboxMessage;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;

public class MaildirMessageMapper extends AbstractMessageMapper {

    private final MaildirStore maildirStore;
    private static final int BUF_SIZE = 2048;

    public MaildirMessageMapper(MailboxSession session, MaildirStore maildirStore) {
        super(session, maildirStore, maildirStore);
        this.maildirStore = maildirStore;
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        File newFolder = folder.getNewFolder();
        File curFolder = folder.getCurFolder();
        File[] newFiles = newFolder.listFiles();
        File[] curFiles = curFolder.listFiles();
        if (newFiles == null || curFiles == null) {
            throw new MailboxException("Unable to count messages in Mailbox " + mailbox, new IOException(
                "Not a valid Maildir folder: " + maildirStore.getFolderName(mailbox)));
        }
        return newFiles.length + curFiles.length;
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        File newFolder = folder.getNewFolder();
        File curFolder = folder.getCurFolder();
        String[] unseenMessages = curFolder.list(MaildirMessageName.FILTER_UNSEEN_MESSAGES);
        String[] newUnseenMessages = newFolder.list(MaildirMessageName.FILTER_UNSEEN_MESSAGES);
        if (newUnseenMessages == null || unseenMessages == null) {
            throw new MailboxException("Unable to count unseen messages in Mailbox " + mailbox, new IOException(
                "Not a valid Maildir folder: " + maildirStore.getFolderName(mailbox)));
        }
        return newUnseenMessages.length + unseenMessages.length;
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            folder.delete(message.getUid());
        } catch (MailboxException e) {
            throw new MailboxException("Unable to delete MailboxMessage " + message + " in Mailbox " + mailbox, e);
        }
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        final List<MailboxMessage> results;
        final MessageUid from = set.getUidFrom();
        final MessageUid to = set.getUidTo();
        final Type type = set.getType();
        switch (type) {
        default:
        case ALL:
            results = findMessagesInMailboxBetweenUIDs(mailbox, null, MessageUid.MIN_VALUE, null, max);
            break;
        case FROM:
            results = findMessagesInMailboxBetweenUIDs(mailbox, null, from, null, max);
            break;
        case ONE:
            results = findMessageInMailboxWithUID(mailbox, from);
            break;
        case RANGE:
            results = findMessagesInMailboxBetweenUIDs(mailbox, null, from, to, max);
            break;
        }
        return results.iterator();

    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        SortedMap<MessageUid, MaildirMessageName> recentMessageNames = folder.getRecentMessages();
        return new ArrayList<>(recentMessageNames.keySet());

    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        List<MailboxMessage> result = findMessagesInMailbox(mailbox, MaildirMessageName.FILTER_UNSEEN_MESSAGES, 1);
        if (result.isEmpty()) {
            return null;
        } else {
            return result.get(0).getUid();
        }
    }

    @Override
    public List<MailboxCounters> getMailboxCounters(Collection<Mailbox> mailboxes) throws MailboxException {
        return mailboxes.stream()
            .map(Throwing.<Mailbox, MailboxCounters>function(this::getMailboxCounters).sneakyThrow())
            .collect(Guavate.toImmutableList());
    }

    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) throws MailboxException {
        final List<UpdatedFlags> updatedFlags = new ArrayList<>();
        final MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);

        Iterator<MailboxMessage> it = findInMailbox(mailbox, set, FetchType.Metadata, UNLIMITED);
        while (it.hasNext()) {
            final MailboxMessage member = it.next();
            Flags originalFlags = member.createFlags();
            member.setFlags(flagsUpdateCalculator.buildNewFlags(originalFlags));
            Flags newFlags = member.createFlags();

            try {
                MaildirMessageName messageName = folder.getMessageNameByUid(member.getUid());
                if (messageName != null) {
                    File messageFile = messageName.getFile();
                    messageName.setFlags(member.createFlags());
                    // this automatically moves messages from new to cur if
                    // needed
                    String newMessageName = messageName.getFullName();

                    File newMessageFile;

                    // See MAILBOX-57
                    if (newFlags.contains(Flag.RECENT)) {
                        // message is recent so save it in the new folder
                        newMessageFile = new File(folder.getNewFolder(), newMessageName);
                    } else {
                        newMessageFile = new File(folder.getCurFolder(), newMessageName);
                    }
                    long modSeq;
                    // if the flags don't have change we should not try to move
                    // the file
                    if (newMessageFile.equals(messageFile) == false) {
                        FileUtils.moveFile(messageFile, newMessageFile);
                        modSeq = newMessageFile.lastModified();

                    } else {
                        modSeq = messageFile.lastModified();
                    }
                    member.setModSeq(ModSeq.of(modSeq));

                    updatedFlags.add(UpdatedFlags.builder()
                        .uid(member.getUid())
                        .modSeq(member.getModSeq())
                        .newFlags(newFlags)
                        .oldFlags(originalFlags)
                        .build());

                    MessageUid uid = member.getUid();
                    folder.update(uid, newMessageName);
                }
            } catch (IOException e) {
                throw new MailboxException("Failure while save MailboxMessage " + member + " in Mailbox " + mailbox, e);
            }

        }
        return updatedFlags.iterator();

    }

    @Override
    public List<MessageUid> retrieveMessagesMarkedForDeletion(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
        List<MailboxMessage> messages = findDeletedMessages(mailbox, messageRange);
        return getUidList(messages);
    }

    @Override
    public Map<MessageUid, MessageMetaData> deleteMessages(Mailbox mailbox, List<MessageUid> uids) throws MailboxException {
        Map<MessageUid, MessageMetaData> data = new HashMap<>();
        List<MessageRange> ranges = MessageRange.toRanges(uids);

        for (MessageRange range : ranges) {
            List<MailboxMessage> messages = findDeletedMessages(mailbox, range);
            data.putAll(deleteDeletedMessages(mailbox, messages));
        }

        return data;
    }

    private List<MailboxMessage> findDeletedMessages(Mailbox mailbox, MessageRange messageRange) throws MailboxException {
        MessageUid from = messageRange.getUidFrom();
        MessageUid to = messageRange.getUidTo();

        switch (messageRange.getType()) {
            case ONE:
                return findDeletedMessageInMailboxWithUID(mailbox, from);
            case RANGE:
                return findMessagesInMailboxBetweenUIDs(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, from, to, -1);
            case FROM:
                return findMessagesInMailboxBetweenUIDs(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, from, null, -1);
            case ALL:
                return findMessagesInMailbox(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, -1);
            default:
                throw new RuntimeException("Cannot find deleted messages, range type " + messageRange.getType() + " doesn't exist");
        }
    }

    private Map<MessageUid, MessageMetaData> deleteDeletedMessages(Mailbox mailbox, List<MailboxMessage> messages) throws MailboxException {
        return messages.stream()
            .peek(Throwing.<MailboxMessage>consumer(message -> delete(mailbox, message)).sneakyThrow())
            .collect(Guavate.toImmutableMap(MailboxMessage::getUid, MailboxMessage::metaData));
    }

    private List<MessageUid> getUidList(List<MailboxMessage> messages) {
        return messages.stream()
            .map(MailboxMessage::getUid)
            .collect(Guavate.toImmutableList());
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    @Override
    protected MessageMetaData copy(Mailbox mailbox, MessageUid uid, ModSeq modSeq, MailboxMessage original)
            throws MailboxException {
        SimpleMailboxMessage theCopy = SimpleMailboxMessage.copyWithoutAttachments(mailbox.getMailboxId(), original);
        Flags flags = theCopy.createFlags();
        flags.add(Flag.RECENT);
        theCopy.setFlags(flags);
        return save(mailbox, theCopy);
    }

    @Override
    protected MessageMetaData save(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        MessageUid uid = MessageUid.MIN_VALUE;
        // a new message
        // save file to "tmp" folder
        File tmpFolder = folder.getTmpFolder();
        // The only case in which we could get problems with clashing names
        // is if the system clock
        // has been set backwards, then the server is restarted with the
        // same pid, delivers the same
        // number of messages since its start in the exact same millisecond
        // as done before and the
        // random number generator returns the same number.
        // In order to prevent this case we would need to check ALL files in
        // all folders and compare
        // them to this message name. We rather let this happen once in a
        // billion years...
        MaildirMessageName messageName = MaildirMessageName.createUniqueName(folder, message.getFullContentOctets());
        File messageFile = new File(tmpFolder, messageName.getFullName());
        try {
            if (!messageFile.createNewFile()) {
                throw new IOException("Could not create file " + messageFile);
            }
            try (FileOutputStream fos = new FileOutputStream(messageFile);
                InputStream input = message.getFullContent()) {
                byte[] b = new byte[BUF_SIZE];
                int len = 0;
                while ((len = input.read(b)) != -1) {
                    fos.write(b, 0, len);
                }
            }
        } catch (IOException ioe) {
            throw new MailboxException("Failure while save MailboxMessage " + message + " in Mailbox " + mailbox, ioe);
        }
        File newMessageFile = null;
        // delivered via SMTP, goes to ./new without flags
        if (message.isRecent()) {
            messageName.setFlags(message.createFlags());
            newMessageFile = new File(folder.getNewFolder(), messageName.getFullName());
        } else {
            // appended via IMAP (might already have flags etc, goes to ./cur
            // directly)
            messageName.setFlags(message.createFlags());
            newMessageFile = new File(folder.getCurFolder(), messageName.getFullName());
        }
        try {
            FileUtils.moveFile(messageFile, newMessageFile);
        } catch (IOException e) {
            // TODO: Try copy and delete
            throw new MailboxException("Failure while save MailboxMessage " + message + " in Mailbox " + mailbox, e);
        }
        try {
            uid = folder.appendMessage(newMessageFile.getName());
            message.setUid(uid);
            message.setModSeq(ModSeq.of(newMessageFile.lastModified()));
            return message.metaData();
        } catch (MailboxException e) {
            throw new MailboxException("Failure while save MailboxMessage " + message + " in Mailbox " + mailbox, e);
        }

    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        int maxValue = -1;
        return new ApplicableFlagCalculator(findMessagesInMailboxBetweenUIDs(mailbox, null, MessageUid.MIN_VALUE, null, maxValue))
            .computeApplicableFlags();
    }

    @Override
    public void endRequest() {
        // not used

    }

    private List<MailboxMessage> findMessageInMailboxWithUID(Mailbox mailbox, MessageUid from)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            MaildirMessageName messageName = folder.getMessageNameByUid(from);

            ArrayList<MailboxMessage> messages = new ArrayList<>();
            if (messageName != null && messageName.getFile().exists()) {
                messages.add(new MaildirMailboxMessage(mailbox, from, messageName));
            }
            return messages;

        } catch (IOException e) {
            throw new MailboxException("Failure while search for MailboxMessage with uid " + from + " in Mailbox " + mailbox, e);
        }
    }

    private List<MailboxMessage> findMessagesInMailboxBetweenUIDs(Mailbox mailbox, FilenameFilter filter,
                                                                             MessageUid from, MessageUid to, int max) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        int cur = 0;
        SortedMap<MessageUid, MaildirMessageName> uidMap = null;
        try {
            if (filter != null) {
                uidMap = folder.getUidMap(mailboxSession, filter, from, to);
            } else {
                uidMap = folder.getUidMap(from, to);
            }

            ArrayList<MailboxMessage> messages = new ArrayList<>();
            for (Entry<MessageUid, MaildirMessageName> entry : uidMap.entrySet()) {
                messages.add(new MaildirMailboxMessage(mailbox, entry.getKey(), entry.getValue()));
                if (max != -1) {
                    cur++;
                    if (cur >= max) {
                        break;
                    }
                }
            }
            return messages;
        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    private List<MailboxMessage> findMessagesInMailbox(Mailbox mailbox, FilenameFilter filter, int limit)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            SortedMap<MessageUid, MaildirMessageName> uidMap = folder.getUidMap(filter, limit);

            ArrayList<MailboxMessage> filtered = new ArrayList<>(uidMap.size());
            for (Entry<MessageUid, MaildirMessageName> entry : uidMap.entrySet()) {
                filtered.add(new MaildirMailboxMessage(mailbox, entry.getKey(), entry.getValue()));
            }
            return filtered;
        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    private List<MailboxMessage> findDeletedMessageInMailboxWithUID(Mailbox mailbox, MessageUid uid)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            MaildirMessageName messageName = folder.getMessageNameByUid(uid);
            ArrayList<MailboxMessage> messages = new ArrayList<>();
            if (MaildirMessageName.FILTER_DELETED_MESSAGES.accept(null, messageName.getFullName())) {
                messages.add(new MaildirMailboxMessage(mailbox, uid, messageName));
            }
            return messages;

        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    @Override
    protected void begin() throws MailboxException {
        // nothing to do
    }

    @Override
    protected void commit() throws MailboxException {
        // nothing to do
    }

    @Override
    protected void rollback() throws MailboxException {
        // nothing to do
    }

}
