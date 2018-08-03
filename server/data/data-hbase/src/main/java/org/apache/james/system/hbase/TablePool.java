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
package org.apache.james.system.hbase;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.james.domainlist.hbase.def.HDomainList;
import org.apache.james.rrt.hbase.def.HRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.hbase.def.HUsersRepository;

/**
 * Table Pool singleton to get the DomainList, RecipientRewriteTable and UserRepository HBase tables.
 *
 * TODO Two getInstance methods are public, one for the impl, one for the tests. This is not good.
 */
@SuppressWarnings("deprecation")
public class TablePool {

    private static Configuration configuration;
    private static TablePool hbaseSchema;
    private static HTablePool htablePool;

    /**
     * Use getInstance to get an instance of the {@link HTablePool}.
     *
     * Don't give any configuration, the default one will be used
     * via {@link HBaseConfiguration#create(Configuration)}.
     *
     * If you want to create the instance with a specific {@link HBaseConfiguration},
     * use {@link #getInstance(Configuration)}
     *
     * @return An instance using a default configuration
     * @throws IOException
     */
    public static synchronized TablePool getInstance() throws IOException {
        return getInstance(HBaseConfiguration.create());
    }

    /**
     * Use getInstance to get an instance of the {@link HTablePool}.
     *
     * You can give at first call a specific {@link HBaseConfiguration} to suit your needs.
     *
     * @param configuration
     * @return An instance of {@link HTablePool}
     * @throws IOException
     */
    public static synchronized TablePool getInstance(Configuration configuration) throws IOException {
        if (hbaseSchema == null) {
            TablePool.configuration = configuration;
            TablePool.hbaseSchema = new TablePool();
            TablePool.htablePool = new HTablePool(configuration, 100);
            ensureTable(HDomainList.TABLE_NAME, HDomainList.COLUMN_FAMILY_NAME);
            ensureTable(HRecipientRewriteTable.TABLE_NAME, HRecipientRewriteTable.COLUMN_FAMILY_NAME);
            ensureTable(HUsersRepository.TABLE_NAME, HUsersRepository.COLUMN_FAMILY_NAME);
        }
        return hbaseSchema;
    }

    /**
     * Get an instance of the {@link HDomainList} table.
     *
     * @return An instance of {@link HDomainList}
     */
    public HTableInterface getDomainlistTable() {
        return htablePool.getTable(HDomainList.TABLE_NAME);
    }

    /**
     * Get an instance of the RecipientRewriteTable table.
     *
     * @return An instance of {@link RecipientRewriteTable}
     */
    public HTableInterface getRecipientRewriteTable() {
        return htablePool.getTable(HRecipientRewriteTable.TABLE_NAME);
    }

    /**
     * Get an instance of the UsersRepository table.
     *
     * @return An instance of {@link UsersRepository}
     */
    public HTableInterface getUsersRepositoryTable() {
        return htablePool.getTable(HUsersRepository.TABLE_NAME);
    }

    /**
     * Create a table if needed.
     *
     * @param tableName
     * @param columnFamilyName
     * @throws IOException
     */
    private static void ensureTable(byte[] tableName, byte[] columnFamilyName) throws IOException {
        try (HBaseAdmin hbaseAdmin = new HBaseAdmin(configuration)) {
            if (!hbaseAdmin.tableExists(tableName)) {
                HTableDescriptor desc = new HTableDescriptor(tableName);
                HColumnDescriptor hColumnDescriptor = new HColumnDescriptor(columnFamilyName);
                hColumnDescriptor.setMaxVersions(1);
                desc.addFamily(hColumnDescriptor);
                hbaseAdmin.createTable(desc);
            }
        }
    }
}
