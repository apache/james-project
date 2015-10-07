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
import static org.apache.james.mailbox.hbase.HBaseUtils.customMessageRowKey;
import static org.apache.james.mailbox.hbase.HBaseUtils.flagsToPut;
import static org.apache.james.mailbox.hbase.HBaseUtils.messageMetaFromResult;
import static org.apache.james.mailbox.hbase.HBaseUtils.messageRowKey;
import static org.apache.james.mailbox.hbase.HBaseUtils.metadataToPut;

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
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.hbase.io.ChunkOutputStream;
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
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

/**
 * HBase implementation of a {@link MessageMapper}.
 * I don't know if this class is thread-safe! Asume it is not!
 *
 */
public class HBaseMessageMapper extends NonTransactionalMapper implements MessageMapper<HBaseId> {

    private final Configuration conf;
    private final MailboxSession mailboxSession;
    private final UidProvider<HBaseId> uidProvider;
    private final ModSeqProvider<HBaseId> modSeqProvider;

    public HBaseMessageMapper(final MailboxSession session,
            final UidProvider<HBaseId> uidProvider,
            ModSeqProvider<HBaseId> modSeqProvider, Configuration conf) {
        this.mailboxSession = session;
        this.modSeqProvider = modSeqProvider;
        this.uidProvider = uidProvider;
        this.conf = conf;
    }

    @Override
    public void endRequest() {
    }

    @Override
    public Iterator<Message<HBaseId>> findInMailbox(Mailbox<HBaseId> mailbox, MessageRange set, FetchType fType, int max) throws MailboxException {
        try {
            List<Message<HBaseId>> results;
            long from = set.getUidFrom();
            final long to = set.getUidTo();
            final Type type = set.getType();

            switch (type) {
                default:
                case ALL:
                    results = findMessagesInMailbox(mailbox, max, false);
                    break;
                case FROM:
                    results = findMessagesInMailboxAfterUID(mailbox, from, max, false);
                    break;
                case ONE:
                    results = findMessagesInMailboxWithUID(mailbox, from, false);
                    break;
                case RANGE:
                    results = findMessagesInMailboxBetweenUIDs(mailbox, from, to, max, false);
                    break;
            }
            return results.iterator();

        } catch (IOException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    private List<Message<HBaseId>> findMessagesInMailbox(Mailbox<HBaseId> mailbox, int batchSize, boolean flaggedForDelete) throws IOException {
        List<Message<HBaseId>> messageList = new ArrayList<Message<HBaseId>>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        Scan scan = new Scan(customMessageRowKey(mailbox.getMailboxId(), 0L),
                new PrefixFilter(mailbox.getMailboxId().toBytes()));
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
            messageList.add(messageMetaFromResult(conf, result));
            count--;
        }
        scanner.close();
        messages.close();
        // we store uids in reverse order, we send them ascending
        Collections.reverse(messageList);
        return messageList;
    }

    private List<Message<HBaseId>> findMessagesInMailboxWithUID(Mailbox<HBaseId> mailbox, final long messageUid, final boolean flaggedForDelete) throws IOException {
        List<Message<HBaseId>> messageList = new ArrayList<Message<HBaseId>>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        Get get = new Get(messageRowKey(mailbox.getMailboxId(), messageUid));
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
        Message<HBaseId> message = null;
        if (!result.isEmpty()) {
            message = messageMetaFromResult(conf, result);
            messageList.add(message);
        }
        messages.close();
        return messageList;
    }

    private List<Message<HBaseId>> findMessagesInMailboxAfterUID(Mailbox<HBaseId> mailbox, final long from, final int batchSize, final boolean flaggedForDelete) throws IOException {
        List<Message<HBaseId>> messageList = new ArrayList<Message<HBaseId>>();
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        // uids are stored in reverse so we need to search
        Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), Long.MAX_VALUE),
                messageRowKey(mailbox.getMailboxId(), from - 1));
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
            messageList.add(messageMetaFromResult(conf, result));
            count--;
        }
        scanner.close();
        messages.close();
        // uids are stored in reverese so we change the list
        Collections.reverse(messageList);
        return messageList;
    }

    private List<Message<HBaseId>> findMessagesInMailboxBetweenUIDs(Mailbox<HBaseId> mailbox, final long from, final long to, final int batchSize, final boolean flaggedForDelete) throws IOException {
        List<Message<HBaseId>> messageList = new ArrayList<Message<HBaseId>>();
        if (from > to) {
            return messageList;
        }
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), to), messageRowKey(mailbox.getMailboxId(), from - 1));
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
            Message<HBaseId> message = messageMetaFromResult(conf, result);
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
    public Map<Long, MessageMetaData> expungeMarkedForDeletionInMailbox(Mailbox<HBaseId> mailbox, MessageRange set) throws MailboxException {
        try {
            final Map<Long, MessageMetaData> data;
            final List<Message<HBaseId>> results;
            final long from = set.getUidFrom();
            final long to = set.getUidTo();

            switch (set.getType()) {
                case ONE:
                    results = findMessagesInMailboxWithUID(mailbox, from, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxWithUID(mailbox, from);
                    break;
                case RANGE:
                    results = findMessagesInMailboxBetweenUIDs(mailbox, from, to, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxBetweenUIDs(mailbox, from, to);
                    break;
                case FROM:
                    results = findMessagesInMailboxAfterUID(mailbox, from, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailboxAfterUID(mailbox, from);
                    break;
                default:
                case ALL:
                    results = findMessagesInMailbox(mailbox, -1, true);
                    data = createMetaData(results);
                    deleteDeletedMessagesInMailbox(mailbox);
                    break;
            }

            return data;
        } catch (IOException e) {
            throw new MailboxException("Search of MessageRange " + set + " failed in mailbox " + mailbox, e);
        }
    }

    @Override
    public long countMessagesInMailbox(Mailbox<HBaseId> mailbox) throws MailboxException {
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Get get = new Get(mailbox.getMailboxId().toBytes());
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
    public long countUnseenMessagesInMailbox(Mailbox<HBaseId> mailbox) throws MailboxException {
        /* TODO: see if it is possible to store the number of unseen messages in the mailbox table
         * and just return that value with a Get and kepp it up to date.
         */
        HTable messages = null;
        ResultScanner scanner = null;
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), Long.MAX_VALUE),
                    messageRowKey(mailbox.getMailboxId(), 0));
            scan.addFamily(MESSAGES_META_CF);
            scan.setFilter(new SingleColumnValueExcludeFilter(MESSAGES_META_CF, FLAGS_SEEN, CompareOp.EQUAL, MARKER_MISSING));
            scan.setCaching(messages.getScannerCaching() * 2);
            scan.setMaxVersions(1);
            scanner = messages.getScanner(scan);
            long count = 0;
            while (scanner.next() != null) {
                count++;
            }
            return count;
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
    public void delete(Mailbox<HBaseId> mailbox, Message<HBaseId> message) throws MailboxException {
        //TODO: maybe switch to checkAndDelete
        HTable messages = null;
        HTable mailboxes = null;
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            /** TODO: also implement/update the message count for this mailbox
             *  and implement countMessages with get.
             */
            Delete delete = new Delete(messageRowKey(message));
            mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -1);
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
    public Long findFirstUnseenMessageUid(Mailbox<HBaseId> mailbox) throws MailboxException {
        HTable messages = null;
        ResultScanner scanner = null;
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), Long.MAX_VALUE), messageRowKey(mailbox.getMailboxId(), 0));
            scan.addFamily(MESSAGES_META_CF);
            // filter out all rows with FLAGS_SEEN qualifier
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_SEEN, CompareOp.EQUAL, MARKER_MISSING);
            scan.setFilter(filter);
            scan.setCaching(messages.getScannerCaching() * 2);
            scan.setMaxVersions(1);
            scanner = messages.getScanner(scan);
            Result result;
            Long lastUnseen = null;
            byte[] row = null;
            while ((result = scanner.next()) != null) {
                row = result.getRow();
            }
            if (row != null) {
                lastUnseen = Long.MAX_VALUE - Bytes.toLong(row, 16, 8);
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
    public List<Long> findRecentMessageUidsInMailbox(Mailbox<HBaseId> mailbox) throws MailboxException {
        /** TODO: improve performance by implementing a last seen and last recent value per mailbox.
         * maybe one more call to HBase is less expensive than iterating throgh all rows.
         */
        HTable messages = null;
        ResultScanner scanner = null;
        try {
            messages = new HTable(conf, MESSAGES_TABLE);
            /* Limit the number of entries scanned to just the mails in this mailbox */
            Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), Long.MAX_VALUE),
                    messageRowKey(mailbox.getMailboxId(), 0));
            // we add the column, if it exists, the message is recent, else it is not
            scan.addColumn(MESSAGES_META_CF, FLAGS_RECENT);
            SingleColumnValueFilter filter = new SingleColumnValueFilter(MESSAGES_META_CF, FLAGS_RECENT, CompareOp.EQUAL, MARKER_PRESENT);
            scan.setFilter(filter);
            scan.setCaching(messages.getScannerCaching() * 2);
            scan.setMaxVersions(1);

            scanner = messages.getScanner(scan);
            Result result;
            List<Long> uids = new ArrayList<Long>();
            while ((result = scanner.next()) != null) {
                uids.add(Long.MAX_VALUE - Bytes.toLong(result.getRow(), 16, 8));
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
     * @see org.apache.james.mailbox.store.mail.MessageMapper#add(org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData add(Mailbox<HBaseId> mailbox, Message<HBaseId> message) throws MailboxException {
        message.setUid(uidProvider.nextUid(mailboxSession, mailbox));
        // if a mailbox does not support mod-sequences the provider may be null
        if (modSeqProvider != null) {
            message.setModSeq(modSeqProvider.nextModSeq(mailboxSession, mailbox));
        }
        MessageMetaData data = save(mailbox, message);

        return data;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#updateFlags(org.apache.james.mailbox.store.mail.model.Mailbox, javax.mail.Flags, boolean, boolean, org.apache.james.mailbox.MessageRange)
     */
    @Override
    public Iterator<UpdatedFlags> updateFlags(final Mailbox<HBaseId> mailbox, FlagsUpdateCalculator flagsUpdateCalculator, MessageRange set) throws MailboxException {

        final List<UpdatedFlags> updatedFlags = new ArrayList<UpdatedFlags>();
        Iterator<Message<HBaseId>> messagesFound = findInMailbox(mailbox, set, FetchType.Metadata, -1);

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
                final Message<HBaseId> member = messagesFound.next();
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

                UpdatedFlags uFlags = new UpdatedFlags(member.getUid(), member.getModSeq(), originalFlags, newFlags);
                updatedFlags.add(uFlags);
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

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#copy(org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData copy(Mailbox<HBaseId> mailbox, Message<HBaseId> original) throws MailboxException {
        long uid = uidProvider.nextUid(mailboxSession, mailbox);
        long modSeq = -1;
        if (modSeqProvider != null) {
            modSeq = modSeqProvider.nextModSeq(mailboxSession, mailbox);
        }
        //TODO: check if creating a HBase message is the right thing to do
        HBaseMessage message = new HBaseMessage(conf,
                mailbox.getMailboxId(), uid, modSeq, original);
        return save(mailbox, message);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#copy(org.apache.james.mailbox.store.mail.model.Mailbox, org.apache.james.mailbox.store.mail.model.Message)
     */
    @Override
    public MessageMetaData move(Mailbox<HBaseId> mailbox, Message<HBaseId> original) throws MailboxException {
    	//TODO implement if possible
    	throw new UnsupportedOperationException();
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#getLastUid(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public long getLastUid(Mailbox<HBaseId> mailbox) throws MailboxException {
        return uidProvider.lastUid(mailboxSession, mailbox);
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailbox.store.mail.MessageMapper#getHighestModSeq(org.apache.james.mailbox.store.mail.model.Mailbox)
     */
    @Override
    public long getHighestModSeq(Mailbox<HBaseId> mailbox) throws MailboxException {
        return modSeqProvider.highestModSeq(mailboxSession, mailbox);
    }

    /**
     * Save the {@link Message} for the given {@link Mailbox} and return the {@link MessageMetaData}
     *
     * @param mailbox
     * @param message
     * @return metaData
     * @throws MailboxException
     */
    protected MessageMetaData save(Mailbox<HBaseId> mailbox, Message<HBaseId> message) throws MailboxException {
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
            mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, 1);
            return new SimpleMessageMetaData(message);
        } catch (IOException ex) {
            throw new MailboxException("Error setting flags for messages in " + mailbox, ex);
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

    private void deleteDeletedMessagesInMailboxWithUID(Mailbox<HBaseId> mailbox, long uid) throws IOException {
        //TODO: do I have to check if the message is flagged for delete here?
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        Delete delete = new Delete(messageRowKey(mailbox.getMailboxId(), uid));
        messages.delete(delete);
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -1);
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailboxBetweenUIDs(Mailbox<HBaseId> mailbox, long fromUid, long toUid) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), fromUid), messageRowKey(mailbox.getMailboxId(), toUid));
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
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailboxAfterUID(Mailbox<HBaseId> mailbox, long fromUid) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(messageRowKey(mailbox.getMailboxId(), fromUid));
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
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private void deleteDeletedMessagesInMailbox(Mailbox<HBaseId> mailbox) throws IOException {
        HTable messages = new HTable(conf, MESSAGES_TABLE);
        HTable mailboxes = new HTable(conf, MAILBOXES_TABLE);
        List<Delete> deletes = new ArrayList<Delete>();
        /*TODO: check if Between should be inclusive or exclusive regarding limits.
         * HBase scan operaion are exclusive to the upper bound when providing stop row key.
         */
        Scan scan = new Scan(customMessageRowKey(mailbox.getMailboxId(), 0L),
                new PrefixFilter(mailbox.getMailboxId().toBytes()));
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
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_MESSAGE_COUNT, -(totalDeletes - deletes.size()));
        mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
        mailboxes.close();
        messages.close();
    }

    private Map<Long, MessageMetaData> createMetaData(List<Message<HBaseId>> uids) {
        final Map<Long, MessageMetaData> data = new HashMap<Long, MessageMetaData>();
        for (int i = 0; i < uids.size(); i++) {
            Message<HBaseId> m = uids.get(i);
            data.put(m.getUid(), new SimpleMessageMetaData(m));
        }
        return data;
    }
}
