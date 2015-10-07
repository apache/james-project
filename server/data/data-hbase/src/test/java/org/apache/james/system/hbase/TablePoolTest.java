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
import junit.framework.Assert;
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple tests for the TablePool singleton class.
 * Check that the returned singleton and table instances and not null.
 */
public class TablePoolTest {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    @BeforeClass
    public static void setMeUp() throws IOException {
        TablePool.getInstance(cluster.getConf());
    }

    @Test
    public void testGetInstance() throws IOException {
        Assert.assertNotNull(TablePool.getInstance());
    }

    @Test
    public void testGetDomainlistTable() throws IOException {
        Assert.assertNotNull(TablePool.getInstance().getDomainlistTable());
    }

    @Test
    public void testGetRecipientRewriteTable() throws IOException {
        Assert.assertNotNull(TablePool.getInstance().getRecipientRewriteTable());
    }

    @Test
    public void testGetUsersRepositoryTable() throws IOException {
        Assert.assertNotNull(TablePool.getInstance().getUsersRepositoryTable());
    }
}
