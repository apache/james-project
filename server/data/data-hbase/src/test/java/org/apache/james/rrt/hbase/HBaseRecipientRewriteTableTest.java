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
package org.apache.james.rrt.hbase;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.domainlist.hbase.def.HDomainList;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.rrt.hbase.def.HRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.apache.james.system.hbase.TablePool;
import org.apache.james.user.hbase.def.HUsersRepository;
import org.junit.After;
import org.junit.Before;

public class HBaseRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    @Before
    public void setMeUp() throws Exception {
        TablePool.getInstance(cluster.getConf());
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        cluster.clearTable(new String(HDomainList.TABLE_NAME));
        cluster.clearTable(new String(HRecipientRewriteTable.TABLE_NAME));
        cluster.clearTable(new String(HUsersRepository.TABLE_NAME));
        super.tearDown();
    }
    
    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        HBaseRecipientRewriteTable rrt = new HBaseRecipientRewriteTable();
        rrt.configure(new DefaultConfigurationBuilder());
        return rrt;
    }
}
