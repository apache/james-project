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

import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOXES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MAILBOX_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_META_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGES_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_BODY_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.MESSAGE_DATA_HEADERS_CF;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTIONS_TABLE;
import static org.apache.james.mailbox.hbase.HBaseNames.SUBSCRIPTION_CF;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.io.IOUtils;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.SubscriptionException;
import org.apache.james.mailbox.hbase.mail.HBaseMailboxMapper;
import org.apache.james.mailbox.hbase.mail.HBaseMessageMapper;
import org.apache.james.mailbox.hbase.user.HBaseSubscriptionMapper;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.mailbox.store.user.SubscriptionMapper;

/**
 * HBase implementation of {@link MailboxSessionMapperFactory}
 *
 */
public class HBaseMailboxSessionMapperFactory extends MailboxSessionMapperFactory<HBaseId> {

    private final Configuration conf;
    private final UidProvider<HBaseId> uidProvider;
    private final ModSeqProvider<HBaseId> modSeqProvider;

    /**
     * Creates  the necessary tables in HBase if they do not exist.
     *
     * @param conf Configuration for the cluster
     * @param uidProvider UID provider for mailbox uid.
     * @param modSeqProvider
     * @throws MasterNotRunningException
     * @throws ZooKeeperConnectionException
     * @throws IOException
     */
    public HBaseMailboxSessionMapperFactory(Configuration conf, UidProvider<HBaseId> uidProvider, ModSeqProvider<HBaseId> modSeqProvider) {
        this.conf = conf;
        this.uidProvider = uidProvider;
        this.modSeqProvider = modSeqProvider;

        //TODO: add better exception handling for this
        HBaseAdmin hbaseAdmin = null;
        try {
            hbaseAdmin = new HBaseAdmin(conf);
            HTableDescriptor desc = null;
            HColumnDescriptor hColumnDescriptor = null;

            /* create the tables if it does not exist */

            if (!hbaseAdmin.tableExists(MAILBOXES_TABLE)) {
                desc = new HTableDescriptor(MAILBOXES_TABLE);
                hColumnDescriptor = new HColumnDescriptor(MAILBOX_CF);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hbaseAdmin.createTable(desc);
            }

            if (!hbaseAdmin.tableExists(MESSAGES_TABLE)) {
                /**TODO: try to reduce the number of column families as suggested by:
                 * http://hbase.apache.org/book.html#number.of.cfs
                 * Down to three column families, striking for just two.
                 */
                desc = new HTableDescriptor(MESSAGES_TABLE);
                hColumnDescriptor = new HColumnDescriptor(MESSAGES_META_CF);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hColumnDescriptor = new HColumnDescriptor(MESSAGE_DATA_HEADERS_CF);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hColumnDescriptor = new HColumnDescriptor(MESSAGE_DATA_BODY_CF);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hbaseAdmin.createTable(desc);
            }


            if (!hbaseAdmin.tableExists(SUBSCRIPTIONS_TABLE)) {
                desc = new HTableDescriptor(SUBSCRIPTIONS_TABLE);
                hColumnDescriptor = new HColumnDescriptor(SUBSCRIPTION_CF);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hbaseAdmin.createTable(desc);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.cleanup(null, hbaseAdmin);
        }
    }

    @Override
    public MessageMapper<HBaseId> createMessageMapper(MailboxSession session) throws MailboxException {
        return new HBaseMessageMapper(session, uidProvider, modSeqProvider, this.conf);
    }

    @Override
    public MailboxMapper<HBaseId> createMailboxMapper(MailboxSession session) throws MailboxException {
        return new HBaseMailboxMapper(this.conf);
    }

    @Override
    public SubscriptionMapper createSubscriptionMapper(MailboxSession session) throws SubscriptionException {
        return new HBaseSubscriptionMapper(this.conf);
    }

    /**
     * Returns the configuration object for accessing the cluster.
     * @return The configuration for accessing the cluster
     */
    public Configuration getClusterConfiguration() {
        return conf;
    }

    /**
     * Returns the ModSeqProvider used.
     * @return The used modSeqProvider
     */
    public ModSeqProvider<HBaseId> getModSeqProvider() {
        return modSeqProvider;
    }

    /**
     * Returns the UidProvider that generates UID's for mailboxes.
     * @return The provider that generates UID's for mailboxes
     */
    public UidProvider<HBaseId> getUidProvider() {
        return uidProvider;
    }
}
