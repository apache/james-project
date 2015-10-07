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
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_HIGHEST_MODSEQ;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.hbase.HBaseId;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.model.Mailbox;
/**
 * ModSeqProvider implementation for HBase.
 * 
 */
public class HBaseModSeqProvider implements ModSeqProvider<HBaseId> {

    /** Link to the HBase Configuration object and specific mailbox names */
    private final Configuration conf;

    public HBaseModSeqProvider(Configuration conf) {
        this.conf = conf;
    }

    @Override
    public long highestModSeq(MailboxSession session, Mailbox<HBaseId> mailbox) throws MailboxException {
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            Get get = new Get(mailbox.getMailboxId().toBytes());
            get.addColumn(MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ);
            get.setMaxVersions(1);
            Result result = mailboxes.get(get);

            if (result == null) {
                throw new MailboxException("Row or column not found!");
            }
            long modSeq = Bytes.toLong(result.getValue(MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ));
            return modSeq;
        } catch (IOException e) {
            throw new MailboxException("highestModSeq", e);
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
    public long nextModSeq(MailboxSession session, Mailbox<HBaseId> mailbox) throws MailboxException {
        HTable mailboxes = null;
        try {
            mailboxes = new HTable(conf, MAILBOXES_TABLE);
            long newValue = mailboxes.incrementColumnValue(mailbox.getMailboxId().toBytes(), MAILBOX_CF, MAILBOX_HIGHEST_MODSEQ, 1);
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
