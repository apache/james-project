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
package org.apache.james.mailbox.hbase.mail;

import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_DELETED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_RECENT;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_SEEN;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_HIGHEST_MODSEQ;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_MESSAGE_COUNT;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_MISSING;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_PRESENT;
import static org.apache.james.mailbox.hbase.HBaseNames.MAX_COLUMN_SIZE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_MODSEQ;
import static org.apache.james.mailbox.hbase.HBaseUtils.flagsToPut;
import static org.apache.james.mailbox.hbase.HBaseUtils.messageMetaFromResult;
import static org.apache.james.mailbox.hbase.HBaseUtils.messageRowKey;
import static org.apache.james.mailbox.hbase.HBaseUtils.metadataToPut;
import static org.apache.james.mailbox.hbase.HBaseUtils.minMessageRowKey;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueExcludeFilter;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.io.ChunkOutputStream;
import org.apache.james.mailbox.model.MailboxCounters;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageId.Factory;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.FlagsUpdateCalculator;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.utils.ApplicableFlagCalculator;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

/**
 * HBase implementation of a {@link MessageMapper}.
 * I don't know if this class is thread-safe! Asume it is not!
 *
 */
public class HBaseMessageMapper extends NonTransactionalMapper implements MessageMapper {

    private static final int UNLIMITED = -1;

    private final Configuration conf;
    private final MailboxSession mailboxSession;
    private final UidProvider uidProvider;
    private final ModSeqProvider modSeqProvider;
    private final Factory messageIdFactory;

    public HBaseMessageMapper(MailboxSession session,
            final UidProvider uidProvider,
            ModSeqProvider modSeqProvider, 
            MessageId.Factory messageIdFactory, 
            Configuration conf) {
        this.mailboxSession = session;
        this.modSeqProvider = modSeqProvider;
        this.uidProvider = uidProvider;
        this.messageIdFactory = messageIdFactory;
        this.conf = conf;
    }

    @Override
    public MailboxCounters getMailboxCounters(Mailbox mailbox) throws MailboxException {
        return MailboxCounters.builder()
            .count(countMessagesInMailbox(mailbox))
            .unseen(countUnseenMessagesInMailbox(mailbox))
            .build();
    }

    @Override
    public Iterator<MessageUid> listAllMessageUids(final Mailbox mailbox) throws MailboxException {
        return Iterators.transform(findInMailbox(mailbox, MessageRange.all(), FetchType.Full, UNLIMITED), MailboxMessage::getUid);
    }

    @Override
    public void endRequest() {
    }

    @Override
    public Iterator<MailboxMessage> findInMailbox(Mailbox mailbox, MessageRange set, FetchType fType, int max) throws MailboxException {
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            List<MailboxMessage> results;
            MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
            final Type type = set.getType();

            switch (type) {
                default:
                case ALL:
                    results = findMessagesInMailbox(mailboxId, max, false);
                    break;
                case FROM:
                    results = findMessagesInMailboxAfterUID(mailboxId, from, max, false);
                    break;
                case ONE:
                    results = findMessagesInMailboxWithUID(mailboxId, from, false);
                    break;
                case RANGE:
                    results = findMessagesInMailboxBetweenUIDs(mailboxId, from, to, max, false);
                    break;
            }
            return results.iterator();

        } catch (IOException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    private List<MailboxMessage> findMessagesInMailbox(HBaseId mailboxId, int batchSize, boolean flaggedForDelete) throws IOException {
        List<MailboxMessage> messageList = new ArrayList<MailboxMessage>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        Scan scan = new Scan(minMessageRowKey(mailboxId),
                new PrefixFilter(mailboxId.toBytes()));
        if (flaggedForDelete) {
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
            filter.setFilterIfMissing(true);
            scan.setFilter(filter);
        }
        scan.setMaxVersions(1);
        /* we exclude the message content column family because it could be too large.
         * the content will be pulled from HBase on demand by using a a ChunkedInputStream implementation.
         */
        scan.addFamily(MESSAGES_META_CF);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;
        long count = batchSize > 0 ? batchSize : Long.MAX_VALUE;
        while (((result = scanner.next()) != null) && (count > 0)) {
            messageList.add(messageMetaFromResult(conf, result, messageIdFactory));
            count--;
        }
        scanner.close();
        messages.close();
        // we store uids in reverse order, we send them ascending
        Collections.reverse(messageList);
        return messageList;
    }

    private List<MailboxMessage> findMessagesInMailboxWithUID(HBaseId mailboxId, MessageUid from, boolean flaggedForDelete) throws IOException {
        List<MailboxMessage> messageList = new ArrayList<MailboxMessage>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        Get get = new Get(messageRowKey(mailboxId, from));
        get.setMaxVersions(1);
        /* we exclude the message content column family because it could be too large.
         * the content will be pulled from HBase on demand by using a a ChunkedInputStream implementation.
         */
        if (flaggedForDelete) {
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
            filter.setFilterIfMissing(true);
            get.setFilter(filter);
        }
        get.addFamily(MESSAGES_META_CF);
        Result result = messages.get(get);
        MailboxMessage message = null;
        if (!result.isEmpty()) {
            message = messageMetaFromResult(conf, result, messageIdFactory);
            messageList.add(message);
        }
        messages.close();
        return messageList;
    }

    private List<MailboxMessage> findMessagesInMailboxAfterUID(HBaseId mailboxId, MessageUid messageUid, int batchSize, boolean flaggedForDelete) throws IOException {
        List<MailboxMessage> messageList = new ArrayList<MailboxMessage>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        // uids are stored in reverse so we need to search
        
        Scan scan = new Scan(messageRowKey(mailboxId, MessageUid.MAX_VALUE), previousMessageRowKey(mailboxId, messageUid));
        if (flaggedForDelete) {
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
            filter.setFilterIfMissing(true);
            scan.setFilter(filter);
        }
        scan.setMaxVersions(1);
        /* we exclude the message content column family because it could be too large.
         * the content will be pulled from HBase on demand by using a a ChunkedInputStream implementation.
         */
        scan.addFamily(MESSAGES_META_CF);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;
        long count = batchSize > 0 ? batchSize : Long.MAX_VALUE;
        while (((result = scanner.next()) != null) && (count > 0)) {
            messageList.add(messageMetaFromResult(conf, result, messageIdFactory));
            count--;
        }
        scanner.close();
        messages.close();
        // uids are stored in reverese so we change the list
        Collections.reverse(messageList);
        return messageList;
    }

    private byte[] previousMessageRowKey(HBaseId mailboxId, MessageUid messageUid) {
        if (messageUid.isFirst()) {
            return minMessageRowKey(mailboxId);
        } else {
            return messageRowKey(mailboxId, messageUid.previous());
        }
    }

    private List<MailboxMessage> findMessagesInMailboxBetweenUIDs(HBaseId mailboxId, MessageUid from, MessageUid to, int batchSize, boolean flaggedForDelete) throws IOException {
        List<MailboxMessage> messageList = new ArrayList<MailboxMessage>();
        if (from.compareTo(to) > 0) {
            return messageList;
        }
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailboxId, to), previousMessageRowKey(mailboxId, from));
        if (flaggedForDelete) {
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
            filter.setFilterIfMissing(true);
            scan.setFilter(filter);
        }
        scan.setMaxVersions(1);
        /* we exclude the message content column family because it could be too large.
         * the content will be pulled from HBase on demand by using a a ChunkedInputStream implementation.
         */
        scan.addFamily(MESSAGES_META_CF);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;

        long count = batchSize > 0 ? batchSize : Long.MAX_VALUE;
        while (((result = scanner.next()) != null)) {
            if (count == 0) {
                break;
            }
            MailboxMessage message = messageMetaFromResult(conf, result, messageIdFactory);
            messageList.add(message);
            count--;
        }
        scanner.close();
        messages.close();
        // uids are stored in reverse order
        Collections.reverse(messageList);
        return messageList;
    }

    @Override
    public Map<MessageUid, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox mailbox, MessageRange set) throws MailboxException {
        try {
            final Map<MessageUid, MessageMetaData> data;
            final List<MailboxMessage> results;
            final MessageUid from = set.getUidFrom();
            final MessageUid to = set.getUidTo();
            HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();

            switch (set.getType()) {
                case ONE:
                    results = findMessagesInMailboxWithUID(mailboxId, from, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxWithUID(mailboxId, from);
                    break;
                case RANGE:
                    results = findMessagesInMailboxBetweenUIDs(mailboxId, from, to, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxBetweenUIDs(mailboxId, from, to);
                    break;
                case FROM:
                    results = findMessagesInMailboxAfterUID(mailboxId, from, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxAfterUID(mailboxId, from);
                    break;
                default:
                case ALL:
                    results = findMessagesInMailbox(mailboxId, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailbox(mailboxId);
                    break;
            }

            return data;
        } catch (IOException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    @Override
    public long countMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        HTable mailboxes = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Get get = new Get(mailboxId.toBytes());
            get.addColumn(MAILBOX_CF, MAILBOX_MESSAGE_COUNT);
            get.setMaxVersions(1);
            Result result = mailboxes.get(get);
            long count = Bytes.toLong(result.getValue(MAILBOX_CF, MAILBOX_MESSAGE_COUNT));
            return count;
        } catch (IOException e) {
            throw new MailboxException("Count of messages failed in mailbox " + mailbox, e);
        } finally {
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
        }
    }

    @Override
    public long countUnseenMessagesInMailbox(Mailbox mailbox) throws MailboxException {
        /* TODO: see if it is possible to store the number of unseen messages in the mailbox table
         * and just return that value with a Get and kepp it up to date.
         */
        HTable messages = null;
        ResultScanner scanner = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(
                    messageRowKey(mailboxId, MessageUid.MAX_VALUE),
                    minMessageRowKey(mailboxId));
            scan.addFamily(MESSAGES_META_CF);
            scan.setFilter(new SingleColumnValueExcludeFilter(MESSAGES_META_CF, FLAGS_SEEN, CompareOp.EQUAL, MARKER_MISSING));
            scan.setCaching(messages.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            scan.setMaxVersions(1);
            scanner = messages.getScanner(scan);
            return Iterables.size(scanner);
        } catch (IOException e) {
            throw new MailboxException("Search of first unseen message failed in mailbox " + mailbox, e);
        } finally {
            scanner.close();
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + messages, ex);
                }
            }
        }
    }

    @Override
    public void delete(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        //TODO: maybe switch to checkAndDelete
        HTable messages = null;
        HTable mailboxes = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            /** TODO: also implement/update the message count for this mailbox
             *  and implement countMessages with get.
             */
            Delete delete = new Delete(messageRowKey(message));
            mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -1);
            messages.delete(delete);

        } catch (IOException ex) {
            throw new MailboxException("Delete of message " + message + " failed in mailbox " + mailbox, ex);
        } finally {

            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + messages, ex);
                }
            }

        }

    }

    @Override
    public MessageUid findFirstUnseenMessageUid(Mailbox mailbox) throws MailboxException {
        HTable messages = null;
        ResultScanner scanner = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(
                    messageRowKey(mailboxId, MessageUid.MAX_VALUE), 
                    minMessageRowKey(mailboxId));
            scan.addFamily(MESSAGES_META_CF);
            // filter out all rows with FLAGS_SEEN qualifier
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_SEEN, CompareOp.EQUAL, MARKER_MISSING);
            scan.setFilter(filter);
            scan.setCaching(messages.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            scan.setMaxVersions(1);
            scanner = messages.getScanner(scan);
            Result result;
            MessageUid lastUnseen = null;
            byte[] row = null;
            while ((result = scanner.next()) != null) {
                row = result.getRow();
            }
            if (row != null) {
                lastUnseen = MessageUid.of(Long.MAX_VALUE - Bytes.toLong(row, 16, 8));
            }
            return lastUnseen;
        } catch (IOException e) {
            throw new MailboxException("Search of first unseen message failed in mailbox " + mailbox, e);
        } finally {
            scanner.close();
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + messages, ex);
                }
            }
        }
    }

    @Override
    public List<MessageUid> findRecentMessageUidsInMailbox(Mailbox mailbox) throws MailboxException {
        /** TODO: improve performance by implementing a last seen and last recent value per mailbox.
         * maybe one more call to HBase is less expensive than iterating throgh all rows.
         */
        HTable messages = null;
        ResultScanner scanner = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(
                    messageRowKey(mailboxId, MessageUid.MAX_VALUE),
                    minMessageRowKey(mailboxId));
            // we add the column, if it exists, the message is recent, else it is not
            scan.addColumn(MESSAGES_META_CF, FLAGS_RECENT);
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_RECENT, CompareOp.EQUAL, MARKER_PRESENT);
            scan.setFilter(filter);
            scan.setCaching(messages.getConfiguration().getInt("hbase.client.scanner.caching", 1) * 2);
            scan.setMaxVersions(1);

            scanner = messages.getScanner(scan);
            Result result;
            List<MessageUid> uids = new ArrayList<MessageUid>();
            while ((result = scanner.next()) != null) {
                uids.add(MessageUid.of(Long.MAX_VALUE - Bytes.toLong(result.getRow(), 16, 8)));
            }
            Collections.reverse(uids);
            return uids;
        } catch (IOException e) {
            throw new MailboxException("Search of recent messages failed in mailbox " + mailbox, e);
        } finally {
            scanner.close();
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + messages, ex);
                }
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#add(org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.store.mail.model.MailboxMessage)
     */
    @Override
    public MessageMetaData add(Mailbox mailbox, MailboxMessage message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        // if a mailbox does not support mod-sequences the provider may be null
        if (modSeqProvider != null) {
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        }
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        MessageMetaData data = save(mailboxId, message);

        return data;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#updateFlags(org.apache.james.mailbox.store.mail.model.Mailbox, javax.mail.Flags, boolean, boolean, org.apache.james.mailbox.MessageRange)
     */
    @Override
    public Iterator<UpdatedFlags> updateFlags(Mailbox mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) throws MailboxException {

        final List<UpdatedFlags> updatedFlags = new ArrayList<UpdatedFlags>();
        Iterator<MailboxMessage> messagesFound = findInMailbox(mailbox, set, FetchType.Metadata, -1);

        HTable messages = null;
        long modSeq = -1;
        if (messagesFound.hasNext() == false) {
            // if a mailbox does not support mod-sequences the provider may be null
            if (modSeqProvider != null) {
                modSeq = modSeqProvider.nextModSeq(mailboxSession, mailbox);
            }
        }

        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            while (messagesFound.hasNext()) {
                Put put = null;
                final MailboxMessage member = messagesFound.next();
                Flags originalFlags = member.createFlags();
                member.setFlags(flagsUpdateCalculator.buildNewFlags(originalFlags));
                Flags newFlags = member.createFlags();
                put = flagsToPut(member, newFlags);
                if (UpdatedFlags.flagsChanged(originalFlags, newFlags)) {
                    // increase the mod-seq as we changed the flags
                    put.add(MESSAGES_META_CF, MESSAGE_MODSEQ, Bytes.toBytes(modSeq));
                    // update put not to include the allready existing flags
                    messages.put(put);
                    messages.flushCommits();
                }

                updatedFlags.add(UpdatedFlags.builder()
                    .uid(member.getUid())
                    .modSeq(member.getModSeq())
                    .newFlags(newFlags)
                    .oldFlags(originalFlags)
                    .build());
            }
        } catch (IOException e) {
            throw new MailboxException("Error setting flags for messages in " + mailbox, e);
        } finally {
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException e) {
                    throw new MailboxException("Error setting flags for messages in " + mailbox, e);
                }
            }
        }

        return updatedFlags.iterator();
    }

    @Override
    public MessageMetaData copy(Mailbox mailbox, MailboxMessage original) throws MailboxException {
        MessageUid uid = uidProvider.nextUid(mailboxSession, mailbox);
        long modSeq = -1;
        if (modSeqProvider != null) {
            modSeq = modSeqProvider.nextModSeq(mailboxSession, mailbox);
        }
        //TODO: check if creating a HBase message is the right thing to do
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        HBaseMailboxMessage message = new HBaseMailboxMessage(conf,
                mailboxId, uid, original.getMessageId(), modSeq, original);
        return save(mailboxId, message);
    }

    @Override
    public MessageMetaData move(Mailbox mailbox, MailboxMessage original) throws MailboxException {
    	//TODO implement if possible
    	throw new UnsupportedOperationException();
    }

    @Override
    public Optional<MessageUid> getLastUid(Mailbox mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    @Override
    public long getHighestModSeq(Mailbox mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    @Override
    public Flags getApplicableFlag(Mailbox mailbox) throws MailboxException {
        int maxBatchSize = -1;
        boolean flaggedForDelete = true;
        try {
            return new ApplicableFlagCalculator(findMessagesInMailbox((HBaseId) mailbox.getMailboxId(), maxBatchSize, flaggedForDelete))
                .computeApplicableFlags();
        } catch (IOException e) {
            throw new MailboxException("Search of all message failed in mailbox " + mailbox.getName(), e);
        }
    }

    /**
     * Save the {@link MailboxMessage} for the given {@link Mailbox} and return the {@link MessageMetaData}
     *
     * @param mailboxId
     * @param message
     * @return metaData
     * @throws MailboxException
     */
    protected MessageMetaData save(HBaseId mailboxId, MailboxMessage message) throws MailboxException {
        HTable messages = null;
        HTable mailboxes = null;
        BufferedInputStream in = null;
        ChunkOutputStream out = null;
        try {
            //TODO: update the mailbox information about messages
            messages = new HTable(conf, MESSAGES_TABLE);
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            //save the message metadata
            Put put = metadataToPut(message);
            messages.put(put);
            //save the message content
            //TODO: current implementation is crude.

            int b;
            out = new ChunkOutputStream(conf,
                    MESSAGES_TABLE, MESSAGE_DATA_BODY_CF, messageRowKey(message), MAX_COLUMN_SIZE);
            in = new BufferedInputStream(message.getBodyContent());
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            in.close();
            out.close();
            out = new ChunkOutputStream(conf,
                    MESSAGES_TABLE, MESSAGE_DATA_HEADERS_CF, messageRowKey(message), MAX_COLUMN_SIZE);
            in = new BufferedInputStream(message.getHeaderContent());
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            in.close();
            out.close();
            // increase the message count for the current mailbox
            mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, 1);
            return new SimpleMessageMetaData(message);
        } catch (IOException ex) {
            throw new MailboxException("Error setting flags for messages in " + mailboxId, ex);
        } finally {
            if (messages != null) {
                try {
                    messages.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + messages, ex);
                }
            }
            if (mailboxes != null) {
                try {
                    mailboxes.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing table " + mailboxes, ex);
                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing Inputtream", ex);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    throw new MailboxException("Error closing OutputStream", ex);
                }
            }
        }
    }

    private void deleteDeletedMessagesInMailboxWithUID(HBaseId mailboxId, MessageUid from) throws IOException {
        //TODO: do I have to check if the message is flagged for delete here?
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        Delete delete = new Delete(messageRowKey(mailboxId, from));
        messages.delete(delete);
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -1);
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailboxBetweenUIDs(HBaseId mailboxId, MessageUid fromUid, MessageUid toUid) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailboxId, fromUid), messageRowKey(mailboxId, toUid));
        scan.addColumn(MESSAGES_META_CF, FLAGS_DELETED);
        SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
        scan.setFilter(filter);
        scan.setMaxVersions(1);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            deletes.add(new Delete(result.getRow()));
        }
        long totalDeletes = deletes.size();
        scanner.close();
        messages.delete(deletes);
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailboxAfterUID(HBaseId mailboxId, MessageUid fromUid) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailboxId, fromUid));
        scan.addColumn(MESSAGES_META_CF, FLAGS_DELETED);
        SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
        scan.setFilter(filter);
        scan.setMaxVersions(1);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            deletes.add(new Delete(result.getRow()));
        }
        long totalDeletes = deletes.size();
        scanner.close();
        messages.delete(deletes);
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailbox(HBaseId mailboxId) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(minMessageRowKey(mailboxId),
                new PrefixFilter(mailboxId.toBytes()));
        scan.addColumn(MESSAGES_META_CF, FLAGS_DELETED);
        SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_DELETED, CompareOp.EQUAL, MARKER_PRESENT);
        scan.setFilter(filter);
        scan.setMaxVersions(1);
        ResultScanner scanner = messages.getScanner(scan);
        Result result;
        while ((result = scanner.next()) != null) {
            deletes.add(new Delete(result.getRow()));
        }
        long totalDeletes = deletes.size();
        scanner.close();
        messages.delete(deletes);
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailboxId.toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private Map<MessageUid, MessageMetaData> createMetaData(List<MailboxMessage> uids) {
        final Map<MessageUid, MessageMetaData> data = new HashMap<MessageUid, MessageMetaData>();
        for (MailboxMessage m : uids) {
            data.put(m.getUid(), new SimpleMessageMetaData(m));
        }
        return data;
    }
}
