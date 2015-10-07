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
package org.apache.james.mailbox.hbase;

import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_ANSWERED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_DELETED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_DRAFT;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_FLAGGED;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_RECENT;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_SEEN;
import static org.apache.james.mailbox.hbase.FlagConvertor.FLAGS_USER;
import static org.apache.james.mailbox.hbase.FlagConvertor.PREFIX_SFLAGS_B;
import static org.apache.james.mailbox.hbase.FlagConvertor.PREFIX_UFLAGS_B;
import static org.apache.james.mailbox.hbase.FlagConvertor.systemFlagFromBytes;
import static org.apache.james.mailbox.hbase.FlagConvertor.userFlagFromBytes;
import static org.apache.james.mailbox.hbase.FlagConvertor.userFlagToBytes;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_HIGHEST_MODSEQ;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_LASTUID;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_MESSAGE_COUNT;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAME;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_NAMESPACE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_UIDVALIDITY;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_USER;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_MISSING;
import static org.apache.james.mailbox.hbase.HBaseNames.MARKER_PRESENT;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_BODY_OCTETS;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_CONTENT_OCTETS;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_INTERNALDATE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_MEDIA_TYPE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_MODSEQ;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_SUB_TYPE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_TEXT_LINE_COUNT;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;
import static org.apache.james.mailbox.hbase.PropertyConvertor.PREFIX_PROP_B;
import static org.apache.james.mailbox.hbase.PropertyConvertor.getProperty;
import static org.apache.james.mailbox.hbase.PropertyConvertor.getQualifier;
import static org.apache.james.mailbox.hbase.PropertyConvertor.getValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.NavigableMap;
import java.util.UUID;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.hbase.io.ChunkInputStream;
import org.apache.james.mailbox.hbase.mail.HBaseMessage;
import org.apache.james.mailbox.hbase.mail.model.HBaseMailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.user.model.Subscription;

/**
 * HBase utility classes for mailbox and message manipulation.
 * @author ieugen
 */
public class HBaseUtils {
    // TODO: swith to a bit wise implementation of flags.

    /**
     * Creates a Mailbox object from a HBase Result object.
     * @param result a result of a HBase Get operation 
     * @return a Mailbox object
     */
    public static Mailbox<HBaseId> mailboxFromResult(Result result) {
        NavigableMap<byte[], byte[]> rawMailbox = result.getFamilyMap(MAILBOX_CF);
        //TODO: should we test for null values?
        MailboxPath path = new MailboxPath(Bytes.toString(rawMailbox.get(MAILBOX_NAMESPACE)),
                Bytes.toString(rawMailbox.get(MAILBOX_USER)),
                Bytes.toString(rawMailbox.get(MAILBOX_NAME)));

        HBaseMailbox mailbox = new HBaseMailbox(path, Bytes.toLong(rawMailbox.get(MAILBOX_UIDVALIDITY)));
        mailbox.setMailboxId(HBaseIdFromRowKey(result.getRow()));
        mailbox.setHighestModSeq(Bytes.toLong(rawMailbox.get(MAILBOX_HIGHEST_MODSEQ)));
        mailbox.setLastUid(Bytes.toLong(rawMailbox.get(MAILBOX_LASTUID)));
        mailbox.setMessageCount(Bytes.toLong(rawMailbox.get(MAILBOX_MESSAGE_COUNT)));
        return mailbox;
    }

    /**
     * Returns a UUID from the a byte array.
     * @param rowkey
     * @return UUID calculated from the byte array
     */
    public static HBaseId HBaseIdFromRowKey(byte[] rowkey) {
        return HBaseId.of(new UUID(Bytes.toLong(rowkey, 0), Bytes.toLong(rowkey, 8)));
    }

    /**
     * Transforms the mailbox into a Put operation.
     * @return a Put object
     */
    public static Put toPut(HBaseMailbox mailbox) {
        Put put = new Put(mailbox.getMailboxId().toBytes());
        // we don't store null values and we don't restore them. it's a column based store.
        if (mailbox.getName() != null) {
            put.add(MAILBOX_CF, MAILBOX_NAME, Bytes.toBytes(mailbox.getName()));
        }

        if (mailbox.getUser() != null) {
            put.add(MAILBOX_CF, MAILBOX_USER, Bytes.toBytes(mailbox.getUser()));
        }
        if (mailbox.getNamespace() != null) {
            put.add(MAILBOX_CF, MAILBOX_NAMESPACE, Bytes.toBytes(mailbox.getNamespace()));
        }
        put.add(MAILBOX_CF, MAILBOX_LASTUID, Bytes.toBytes(mailbox.getLastUid()));
        put.add(MAILBOX_CF, MAILBOX_UIDVALIDITY, Bytes.toBytes(mailbox.getUidValidity()));
        put.add(MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, Bytes.toBytes(mailbox.getHighestModSeq()));
        put.add(MAILBOX_CF, MAILBOX_MESSAGE_COUNT, Bytes.toBytes(mailbox.getMessageCount()));
        return put;
    }

    /**
     * Transforms only the metadata into a Put object. The rest of the message will
     * be transfered using multiple Puts if size requires it. 
     * @param message
     * @return a put that contains all metadata information.
     */
    public static Put metadataToPut(Message<HBaseId> message) {
        Put put = new Put(messageRowKey(message));
        // we store the message uid and mailbox uid in the row key
        // store the meta data
        put.add(MESSAGES_META_CF, MESSAGE_MODSEQ, Bytes.toBytes(message.getModSeq()));
        put.add(MESSAGES_META_CF, MESSAGE_INTERNALDATE, Bytes.toBytes(message.getInternalDate().getTime()));
        put.add(MESSAGES_META_CF, MESSAGE_MEDIA_TYPE, Bytes.toBytes(message.getMediaType()));
        put.add(MESSAGES_META_CF, MESSAGE_SUB_TYPE, Bytes.toBytes(message.getSubType()));
        put.add(MESSAGES_META_CF, MESSAGE_CONTENT_OCTETS, Bytes.toBytes(message.getFullContentOctets()));
        put.add(MESSAGES_META_CF, MESSAGE_BODY_OCTETS, Bytes.toBytes(message.getBodyOctets()));
        if (message.getTextualLineCount() != null) {
            put.add(MESSAGES_META_CF, MESSAGE_TEXT_LINE_COUNT, Bytes.toBytes(message.getTextualLineCount()));
        }
        // store system flags in meta and user flags in uflags to avoid name clashes
        Flags flags = message.createFlags();
        // system flags
        if (flags.contains(Flag.ANSWERED)) {
            put.add(MESSAGES_META_CF, FLAGS_ANSWERED, MARKER_PRESENT);
        }
        if (flags.contains(Flag.DELETED)) {
            put.add(MESSAGES_META_CF, FLAGS_DELETED, MARKER_PRESENT);
        }
        if (flags.contains(Flag.DRAFT)) {
            put.add(MESSAGES_META_CF, FLAGS_DRAFT, MARKER_PRESENT);
        }
        if (flags.contains(Flag.FLAGGED)) {
            put.add(MESSAGES_META_CF, FLAGS_FLAGGED, MARKER_PRESENT);
        }
        if (flags.contains(Flag.RECENT)) {
            put.add(MESSAGES_META_CF, FLAGS_RECENT, MARKER_PRESENT);
        }
        if (flags.contains(Flag.SEEN)) {
            put.add(MESSAGES_META_CF, FLAGS_SEEN, MARKER_PRESENT);
        }
        if (flags.contains(Flag.USER)) {
            put.add(MESSAGES_META_CF, FLAGS_USER, MARKER_PRESENT);
        }

        // user flags
        for (String flag : flags.getUserFlags()) {
            put.add(MESSAGES_META_CF, userFlagToBytes(flag), MARKER_PRESENT);
        }
        int propNumber = 0;
        // add the properties
        for (Property prop : message.getProperties()) {
            put.add(MESSAGES_META_CF, getQualifier(propNumber++), getValue(prop));
        }

        return put;
    }

    /**
     * Create a row key for a message in a mailbox. The current row key is mailboxID followed by messageID.
     * Both values are fixed length so no separator is needed. 
     * Downside: we will be storing the same message multiple times, one time for each recipient.
     * @param message message to get row key from
     * @return rowkey byte array that can be used with HBase API
     */
    public static byte[] messageRowKey(Message<HBaseId> message) {
        return messageRowKey(message.getMailboxId(), message.getUid());
    }

    /**
     * Utility method to build row keys from mailbox UUID and message uid.
     * The message uid's are stored in reverse order by substracting the uid value 
     * from Long.MAX_VALUE. 
     * @param mailboxUid mailbox UUID
     * @param uid message uid
     * @return rowkey byte array that can be used with HBase API
     */
    public static byte[] messageRowKey(HBaseId mailboxUid, long uid) {
        /**  message uid's are stored in reverse order so we will always have the most recent messages first*/
        return Bytes.add(mailboxUid.toBytes(), Bytes.toBytes(Long.MAX_VALUE - uid));
    }

    /**
     * Utility to build row keys from mailboxUID and a value. The value is added to 
     * the key without any other operations. 
     * @param mailboxUid mailbox HBaseId
     * @param value
     * @return rowkey byte array that can be used with HBase API
     */
    public static byte[] customMessageRowKey(HBaseId mailboxUid, long value) {
        return Bytes.add(mailboxUid.toBytes(), Bytes.toBytes(value));
    }

    /**
     * Creates a HBaseMessage from a Result object. This method retrieves all information 
     * except for body and header related bytes. The message content will be loaded on demand
     * through a specialised InputStream called {@link ChunkInputStream}. 
     * IMPORTANT: the method expects a single version of each cell. Use setMaxVersions(1).
     * @param conf configuration object for HBase cluster
     * @param result the result object containing message data
     * @return a HBaseMessage instance with message metadata.
     */
    public static Message<HBaseId> messageMetaFromResult(Configuration conf, Result result) {
        HBaseMessage message = null;
        Flags flags = new Flags();
        List<Property> propList = new ArrayList<Property>();
        KeyValue[] keys = result.raw();
        String mediaType = null, subType = null;
        Long modSeq = null, uid, bodyOctets = null, contentOctets = null, textualLineCount = null;
        Date internalDate = null;

        int i = 0;
        /** it is VERY IMPORTANT that the byte arrays are kept ascending */
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_BODY_OCTETS)) {
            bodyOctets = Bytes.toLong(keys[i].getValue());
            i++;
        }
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_CONTENT_OCTETS)) {
            contentOctets = Bytes.toLong(keys[i].getValue());
            i++;
        }
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_INTERNALDATE)) {
            internalDate = new Date(Bytes.toLong(keys[i].getValue()));
            i++;
        }
        // may be null so it will probably skip
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_TEXT_LINE_COUNT)) {
            textualLineCount = Bytes.toLong(keys[i].getValue());
            i++;
        }

        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_MODSEQ)) {
            modSeq = Bytes.toLong(keys[i].getValue());
            i++;
        }
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_MEDIA_TYPE)) {
            mediaType = Bytes.toString(keys[i].getValue());
            i++;
        }
        if (Bytes.equals(keys[i].getQualifier(), MESSAGE_SUB_TYPE)) {
            subType = Bytes.toString(keys[i].getValue());
            i++;
        }
        // only TEXT_LINE_COUNT can be missing if message is binary
        if (i < 5) {
            throw new RuntimeException("HBase message column names not sorted.");
        }
        while (i < keys.length) {
            //get message properties
            if (Bytes.startsWith(keys[i].getQualifier(), PREFIX_PROP_B)) {
                propList.add(getProperty(keys[i].getValue()));
            } else if (Bytes.startsWith(keys[i].getQualifier(), PREFIX_SFLAGS_B)) {
                // get system flags, stored as qualifiers
                if (Bytes.equals(MARKER_PRESENT, keys[i].getValue())) {
                    flags.add(systemFlagFromBytes(keys[i].getQualifier()));
                }
            } else if (Bytes.startsWith(keys[i].getQualifier(), PREFIX_UFLAGS_B)) {
                // get user flags, stored as value qualifier
                flags.add(userFlagFromBytes(keys[i].getQualifier()));
            }
            i++;
        }
        HBaseId uuid = HBaseIdFromRowKey(result.getRow());
        uid = Long.MAX_VALUE - Bytes.toLong(result.getRow(), 16);
        PropertyBuilder props = new PropertyBuilder(propList);
        props.setMediaType(mediaType);
        props.setSubType(subType);
        message = new HBaseMessage(conf, uuid, internalDate, flags, contentOctets, (int) (contentOctets - bodyOctets), props);
        message.setUid(uid);
        message.setModSeq(modSeq);
        message.setTextualLineCount(textualLineCount);
        return message;
    }

    /**
     * Creates a Put object from this subscription object
     * @return Put object suitable for HBase persistence
     */
    public static Put toPut(Subscription subscription) {
        Put put = new Put(Bytes.toBytes(subscription.getUser()));
        put.add(SUBSCRIPTION_CF, Bytes.toBytes(subscription.getMailbox()), MARKER_PRESENT);
        return put;
    }

    /**
     * Utility method to transform message flags into a put opperation.
     * @param message
     * @param flags
     * @return a put object with 
     */
    public static Put flagsToPut(Message<HBaseId> message, Flags flags) {
        Put put = new Put(messageRowKey(message));
        //system flags
        if (flags.contains(Flag.ANSWERED)) {
            put.add(MESSAGES_META_CF, FLAGS_ANSWERED, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_ANSWERED, MARKER_MISSING);
        }
        if (flags.contains(Flag.DELETED)) {
            put.add(MESSAGES_META_CF, FLAGS_DELETED, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_DELETED, MARKER_MISSING);
        }
        if (flags.contains(Flag.DRAFT)) {
            put.add(MESSAGES_META_CF, FLAGS_DRAFT, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_DRAFT, MARKER_MISSING);
        }
        if (flags.contains(Flag.FLAGGED)) {
            put.add(MESSAGES_META_CF, FLAGS_FLAGGED, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_FLAGGED, MARKER_MISSING);
        }
        if (flags.contains(Flag.RECENT)) {
            put.add(MESSAGES_META_CF, FLAGS_RECENT, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_RECENT, MARKER_MISSING);
        }
        if (flags.contains(Flag.SEEN)) {
            put.add(MESSAGES_META_CF, FLAGS_SEEN, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_SEEN, MARKER_MISSING);
        }
        if (flags.contains(Flag.USER)) {
            put.add(MESSAGES_META_CF, FLAGS_USER, MARKER_PRESENT);
        } else {
            put.add(MESSAGES_META_CF, FLAGS_USER, MARKER_MISSING);
        }
        /**TODO: user flags are not deleted this way: store them all in a single column  
         * and replace that column full.
         */
        // user flags
        for (String flag : flags.getUserFlags()) {
            put.add(MESSAGES_META_CF, userFlagToBytes(flag), MARKER_PRESENT);
        }
        return put;
    }

    public static Delete flagsToDelete(Message<HBaseId> message, Flags flags) {
        Delete delete = new Delete(messageRowKey(message));
        //we mark for delete flags that are not present (they will be Put'ed)
        if (flags.contains(Flag.ANSWERED)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_ANSWERED);
        }
        if (flags.contains(Flag.DELETED)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_DELETED);
        }
        if (flags.contains(Flag.DRAFT)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_DRAFT);
        }
        if (flags.contains(Flag.FLAGGED)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_FLAGGED);
        }
        if (flags.contains(Flag.RECENT)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_RECENT);
        }
        if (flags.contains(Flag.SEEN)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_SEEN);
        }
        if (flags.contains(Flag.USER)) {
            delete.deleteColumn(MESSAGES_META_CF, FLAGS_USER);
        }

        // we delete all user flags that where not in the new configuration
        for (String flag : flags.getUserFlags()) {
            delete.deleteColumn(MESSAGES_META_CF, userFlagToBytes(flag));
        }
        return delete;
    }

    /**
     * Returns a String composed of all flags in the  parameter.
     * @param flags
     * @return a string representation of all flags
     */
    public static String flagsToString(Flags flags) {
        StringBuilder b = new StringBuilder();

        if (flags.contains(Flag.ANSWERED)) {
            b.append("ANSWERED ");
        }
        if (flags.contains(Flag.DELETED)) {
            b.append("DELETED ");
        }
        if (flags.contains(Flag.DRAFT)) {
            b.append("DRAFT ");
        }
        if (flags.contains(Flag.FLAGGED)) {
            b.append("FLAGGED ");
        }
        if (flags.contains(Flag.RECENT)) {
            b.append("RECENT ");
        }
        if (flags.contains(Flag.SEEN)) {
            b.append("SEEN ");
        }
        if (flags.contains(Flag.USER)) {
            b.append("USER ");
        }
        for (String flag : flags.getUserFlags()) {
            b.append(flag);
            b.append(" ");
        }
        return b.toString();
    }
}
