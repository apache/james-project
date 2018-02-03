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

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_LASTUID;

import java.io.IOException;
import java.util.Optional;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;

/**
 * Message UidProvider for HBase.
 * 
 */
public class HBaseUidProvider implements UidProvider {

    /** Link to the HBase Configuration object and specific mailbox names */
    private final Configuration conf;

    public HBaseUidProvider(Configuration conf) {
        this.conf = conf;
    }

    /**
     * Returns the last message uid used in a mailbox.
     * @param session the session
     * @param mailbox the mailbox for which to get the last uid
     * @return the last uid used
     */
    @Override
    public Optional<MessageUid> lastUid(MailboxSession session, Mailbox mailbox) throws MailboxException {
        HTable mailboxes = null;
        HBaseId mailboxId = (HBaseId) mailbox.getMailboxId();
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Get get = new Get(mailboxId.toBytes());
            get.addColumn(MAILBOX_CF, MAILBOX_LASTUID);
            get.setMaxVersions(1);
            Result result = mailboxes.get(get);

            if (result == null) {
                throw new MailboxException("Row or column not found!");
            }
            long rawUid = Bytes.toLong(result.getValue(MAILBOX_CF, MAILBOX_LASTUID));
            if (rawUid == 0) {
                return Optional.empty();
            }
            return Optional.of(MessageUid.of(rawUid));
        } catch (IOException e) {
            throw new MailboxException("lastUid", e);
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

    /**
     * Returns the next uid. Implemented using HTable.incrementColumnValue(row, family, qualifier, amount).
     * 
     * @param session the mailbox session
     * @param mailbox the mailbox for which we are getting the next uid.
     * @return the next uid to be used.
     * @throws MailboxException 
     */
    @Override
    public MessageUid nextUid(MailboxSession session, Mailbox mailbox) throws MailboxException {
        return nextUid(session, mailbox.getMailboxId());
    }

    @Override
    public MessageUid nextUid(MailboxSession session, MailboxId mailboxId) throws MailboxException {
        HBaseId hbaseId = (HBaseId) mailboxId;
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            MessageUid newValue = MessageUid.of(mailboxes.incrementColumnValue(hbaseId.toBytes(), MAILBOX_CF, MAILBOX_LASTUID, 1));
            mailboxes.close();
            return newValue;
        } catch (IOException e) {
            throw new MailboxException("lastUid", e);
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


}
