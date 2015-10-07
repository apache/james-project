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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.maildir.MaildirFolder;
import org.apache.james.mailbox.maildir.MaildirId;
import org.apache.james.mailbox.maildir.MaildirMessageName;
import org.apache.james.mailbox.maildir.MaildirStore;
import org.apache.james.mailbox.maildir.mail.model.MaildirMessage;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.AbstractMessageMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMessage;

public class MaildirMessageMapper extends AbstractMessageMapper<MaildirId> {

    private final MaildirStore maildirStore;
    private final static int BUF_SIZE = 2048;

    public MaildirMessageMapper(MailboxSession session, MaildirStore maildirStore) {
        super(session, maildirStore, maildirStore);
        this.maildirStore = maildirStore;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countMessagesInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public long countMessagesInMailbox(Mailbox<MaildirId> mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        File newFolder = folder.getNewFolder();
        File curFolder = folder.getCurFolder();
        File[] newFiles = newFolder.listFiles();
        File[] curFiles = curFolder.listFiles();
        if (newFiles == null || curFiles == null)
            throw new MailboxException("Unable to count messages in Mailbox " + mailbox, new IOException(
                    "Not a valid Maildir folder: " + maildirStore.getFolderName(mailbox)));
        int count = newFiles.length + curFiles.length;
        return count;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#countUnseenMessagesInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public long countUnseenMessagesInMailbox(Mailbox<MaildirId> mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        File newFolder = folder.getNewFolder();
        File curFolder = folder.getCurFolder();
        String[] unseenMessages = curFolder.list(MaildirMessageName.FILTER_UNSEEN_MESSAGES);
        String[] newUnseenMessages = newFolder.list(MaildirMessageName.FILTER_UNSEEN_MESSAGES);
        if (newUnseenMessages == null || unseenMessages == null)
            throw new MailboxException("Unable to count unseen messages in Mailbox " + mailbox, new IOException(
                    "Not a valid Maildir folder: " + maildirStore.getFolderName(mailbox)));
        int count = newUnseenMessages.length + unseenMessages.length;
        return count;
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#delete(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public void delete(Mailbox<MaildirId> mailbox, Message<MaildirId> message) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            folder.delete(mailboxSession, message.getUid());
        } catch (MailboxException e) {
            throw new MailboxException("Unable to delete Message " + message + " in Mailbox " + mailbox, e);
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.model.MessageRange,
     *      org.apache.james.mailbox.store.mail.MessageMapper.FetchType, int)
     */
    @Override
    public Iterator<Message<MaildirId>> findInMailbox(Mailbox<MaildirId> mailbox, MessageRange set, FetchType fType, int max)
            throws MailboxException {
        final List<Message<MaildirId>> results;
        final long from = set.getUidFrom();
        final long to = set.getUidTo();
        final Type type = set.getType();
        switch (type) {
        default:
        case ALL:
            results = findMessagesInMailboxBetweenUIDs(mailbox, null, 0, -1, max);
            break;
        case FROM:
            results = findMessagesInMailboxBetweenUIDs(mailbox, null, from, -1, max);
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

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findRecentMessageUidsInMailbox(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<MaildirId> mailbox) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        SortedMap<Long, MaildirMessageName> recentMessageNames = folder.getRecentMessages(mailboxSession);
        return new ArrayList<Long>(recentMessageNames.keySet());

    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#findFirstUnseenMessageUid(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public Long findFirstUnseenMessageUid(Mailbox<MaildirId> mailbox) throws MailboxException {
        List<Message<MaildirId>> result = findMessagesInMailbox(mailbox, MaildirMessageName.FILTER_UNSEEN_MESSAGES, 1);
        if (result.isEmpty()) {
            return null;
        } else {
            return result.get(0).getUid();
        }
    }

    /**
     * @see org.apache.james.mailbox.store.mail.MessageMapper#updateFlags(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      javax.mail.Flags, boolean, boolean,
     *      org.apache.james.mailbox.model.MessageRange)
     */
    @Override
    public Iterator<UpdatedFlags> updateFlags(final Mailbox<MaildirId> mailbox, final FlagsUpdateCalculator flagsUpdateCalculator, final MessageRange set) throws MailboxException {
        final List<UpdatedFlags> updatedFlags = new ArrayList<UpdatedFlags>();
        final MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);

        Iterator<Message<MaildirId>> it = findInMailbox(mailbox, set, FetchType.Metadata, -1);
        while (it.hasNext()) {
            final Message<MaildirId> member = it.next();
            Flags originalFlags = member.createFlags();
            member.setFlags(flagsUpdateCalculator.buildNewFlags(originalFlags));
            Flags newFlags = member.createFlags();

            try {
                MaildirMessageName messageName = folder.getMessageNameByUid(mailboxSession, member.getUid());
                if (messageName != null) {
                    File messageFile = messageName.getFile();
                    // System.out.println("save existing " + message +
                    // " as " + messageFile.getName());
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
                    member.setModSeq(modSeq);

                    updatedFlags.add(new UpdatedFlags(member.getUid(), modSeq, originalFlags, newFlags));

                    long uid = member.getUid();
                    folder.update(mailboxSession, uid, newMessageName);
                }
            } catch (IOException e) {
                throw new MailboxException("Failure while save Message " + member + " in Mailbox " + mailbox, e);
            }

        }
        return updatedFlags.iterator();

    }

    @Override
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<MaildirId> mailbox, MessageRange set)
            throws MailboxException {
        List<Message<MaildirId>> results = new ArrayList<Message<MaildirId>>();
        final long from = set.getUidFrom();
        final long to = set.getUidTo();
        final Type type = set.getType();
        switch (type) {
        default:
        case ALL:
            results = findMessagesInMailbox(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, -1);
            break;
        case FROM:
            results = findMessagesInMailboxBetweenUIDs(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, from, -1,
                    -1);
            break;
        case ONE:
            results = findDeletedMessageInMailboxWithUID(mailbox, from);
            break;
        case RANGE:
            results = findMessagesInMailboxBetweenUIDs(mailbox, MaildirMessageName.FILTER_DELETED_MESSAGES, from, to,
                    -1);
            break;
        }
        Map<Long, MessageMetaData> uids = new HashMap<Long, MessageMetaData>();
        for (int i = 0; i < results.size(); i++) {
            Message<MaildirId> m = results.get(i);
            long uid = m.getUid();
            uids.put(uid, new SimpleMessageMetaData(m));
            delete(mailbox, m);
        }

        return uids;
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.apache.james.mailbox.store.mail.MessageMapper#move(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData move(Mailbox<MaildirId> mailbox, Message<MaildirId> original) throws MailboxException {
        throw new UnsupportedOperationException("Not implemented - see https://issues.apache.org/jira/browse/IMAP-370");
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#copy(org.apache
     *      .james.mailbox.store.mail.model.Mailbox, long, long,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    protected MessageMetaData copy(Mailbox<MaildirId> mailbox, long uid, long modSeq, Message<MaildirId> original)
            throws MailboxException {
        SimpleMessage<MaildirId> theCopy = new SimpleMessage<MaildirId>(mailbox, original);
        Flags flags = theCopy.createFlags();
        flags.add(Flag.RECENT);
        theCopy.setFlags(flags);
        return save(mailbox, theCopy);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.AbstractMessageMapper#save(org.apache.james.mailbox.store.mail.model.Mailbox,
     *      org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    protected MessageMetaData save(Mailbox<MaildirId> mailbox, Message<MaildirId> message) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        long uid = 0;
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
        FileOutputStream fos = null;
        InputStream input = null;
        try {
            if (!messageFile.createNewFile())
                throw new IOException("Could not create file " + messageFile);
            fos = new FileOutputStream(messageFile);
            input = message.getFullContent();
            byte[] b = new byte[BUF_SIZE];
            int len = 0;
            while ((len = input.read(b)) != -1)
                fos.write(b, 0, len);
        } catch (IOException ioe) {
            throw new MailboxException("Failure while save Message " + message + " in Mailbox " + mailbox, ioe);
        } finally {
            try {
                if (fos != null)
                    fos.close();
            } catch (IOException e) {
            }
            try {
                if (input != null)
                    input.close();
            } catch (IOException e) {
            }
        }
        File newMessageFile = null;
        // delivered via SMTP, goes to ./new without flags
        if (message.isRecent()) {
            messageName.setFlags(message.createFlags());
            newMessageFile = new File(folder.getNewFolder(), messageName.getFullName());
            // System.out.println("save new recent " + message + " as " +
            // newMessageFile.getName());
        }
        // appended via IMAP (might already have flags etc, goes to ./cur
        // directly)
        else {
            messageName.setFlags(message.createFlags());
            newMessageFile = new File(folder.getCurFolder(), messageName.getFullName());
            // System.out.println("save new not recent " + message + " as "
            // + newMessageFile.getName());
        }
        try {
            FileUtils.moveFile(messageFile, newMessageFile);
        } catch (IOException e) {
            // TODO: Try copy and delete
            throw new MailboxException("Failure while save Message " + message + " in Mailbox " + mailbox, e);
        }
        try {
            uid = folder.appendMessage(mailboxSession, newMessageFile.getName());
            message.setUid(uid);
            message.setModSeq(newMessageFile.lastModified());
            return new SimpleMessageMetaData(message);
        } catch (MailboxException e) {
            throw new MailboxException("Failure while save Message " + message + " in Mailbox " + mailbox, e);
        }

    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#endRequest()
     */
    @Override
    public void endRequest() {
        // not used

    }

    private List<Message<MaildirId>> findMessageInMailboxWithUID(Mailbox<MaildirId> mailbox, long uid)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            MaildirMessageName messageName = folder.getMessageNameByUid(mailboxSession, uid);

            ArrayList<Message<MaildirId>> messages = new ArrayList<Message<MaildirId>>();
            if (messageName != null && messageName.getFile().exists()) {
                messages.add(new MaildirMessage(mailbox, uid, messageName));
            }
            return messages;

        } catch (IOException e) {
            throw new MailboxException("Failure while search for Message with uid " + uid + " in Mailbox " + mailbox, e);
        }
    }

    private List<Message<MaildirId>> findMessagesInMailboxBetweenUIDs(Mailbox<MaildirId> mailbox, FilenameFilter filter,
            long from, long to, int max) throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        int cur = 0;
        SortedMap<Long, MaildirMessageName> uidMap = null;
        try {
            if (filter != null)
                uidMap = folder.getUidMap(mailboxSession, filter, from, to);
            else
                uidMap = folder.getUidMap(mailboxSession, from, to);

            ArrayList<Message<MaildirId>> messages = new ArrayList<Message<MaildirId>>();
            for (Entry<Long, MaildirMessageName> entry : uidMap.entrySet()) {
                messages.add(new MaildirMessage(mailbox, entry.getKey(), entry.getValue()));
                if (max != -1) {
                    cur++;
                    if (cur >= max)
                        break;
                }
            }
            return messages;
        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    private List<Message<MaildirId>> findMessagesInMailbox(Mailbox<MaildirId> mailbox, FilenameFilter filter, int limit)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            SortedMap<Long, MaildirMessageName> uidMap = folder.getUidMap(mailboxSession, filter, limit);

            ArrayList<Message<MaildirId>> filtered = new ArrayList<Message<MaildirId>>(uidMap.size());
            for (Entry<Long, MaildirMessageName> entry : uidMap.entrySet())
                filtered.add(new MaildirMessage(mailbox, entry.getKey(), entry.getValue()));
            return filtered;
        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    private List<Message<MaildirId>> findDeletedMessageInMailboxWithUID(Mailbox<MaildirId> mailbox, long uid)
            throws MailboxException {
        MaildirFolder folder = maildirStore.createMaildirFolder(mailbox);
        try {
            MaildirMessageName messageName = folder.getMessageNameByUid(mailboxSession, uid);
            ArrayList<Message<MaildirId>> messages = new ArrayList<Message<MaildirId>>();
            if (MaildirMessageName.FILTER_DELETED_MESSAGES.accept(null, messageName.getFullName())) {
                messages.add(new MaildirMessage(mailbox, uid, messageName));
            }
            return messages;

        } catch (IOException e) {
            throw new MailboxException("Failure while search for Messages in Mailbox " + mailbox, e);
        }

    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#begin()
     */
    @Override
    protected void begin() throws MailboxException {
        // nothing todo
    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#commit()
     */
    @Override
    protected void commit() throws MailboxException {
        // nothing todo
    }

    /**
     * @see org.apache.james.mailbox.store.transaction.TransactionalMapper#rollback()
     */
    @Override
    protected void rollback() throws MailboxException {
        // nothing todo
    }

}
