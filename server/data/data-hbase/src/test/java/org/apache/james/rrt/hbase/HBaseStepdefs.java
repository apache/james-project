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

import java.io.IOException;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.domainlist.hbase.def.HDomainList;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.rrt.hbase.def.HRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RewriteTablesStepdefs;
import org.apache.james.system.hbase.TablePool;
import org.apache.james.user.hbase.def.HUsersRepository;

import cucumber.api.java.After;
import cucumber.api.java.Before;

public class HBaseStepdefs {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    private RewriteTablesStepdefs mainStepdefs;

    public HBaseStepdefs(RewriteTablesStepdefs mainStepdefs) {
        try {
            this.mainStepdefs = mainStepdefs;
            TablePool.getInstance(cluster.getConf());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Before
    public void setup() throws Throwable {
        mainStepdefs.rewriteTable = getRecipientRewriteTable(); 
    }

    @After
    public void tearDown() {
        cluster.clearTable(new String(HDomainList.TABLE_NAME));
        cluster.clearTable(new String(HRecipientRewriteTable.TABLE_NAME));
        cluster.clearTable(new String(HUsersRepository.TABLE_NAME));
    }

    private AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        HBaseRecipientRewriteTable rrt = new HBaseRecipientRewriteTable();
        rrt.configure(new DefaultConfigurationBuilder());
        return rrt;
    }
}
