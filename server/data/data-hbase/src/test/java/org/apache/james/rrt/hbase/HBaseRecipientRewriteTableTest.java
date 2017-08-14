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
import org.apache.james.mailbox.hbase.HBaseClusterSingleton;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.apache.james.system.hbase.TablePool;
import org.junit.Before;

/**
 * Tests for the HBase RecipientRewriteTable implementation.
 *
 * Simply create the needed HBaseRecipientRewriteTable instance, and let the
 * AbstractRecipientRewriteTableTest run the tests.
 */
public class HBaseRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    private static final HBaseClusterSingleton cluster = HBaseClusterSingleton.build();

    @Before
    public void setMeUp() throws IOException {
        TablePool.getInstance(cluster.getConf());
    }

    /**
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest#getRecipientRewriteTable()
     */
    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        HBaseRecipientRewriteTable rrt = new HBaseRecipientRewriteTable();
        rrt.configure(new DefaultConfigurationBuilder());
        return rrt;
    }

    /**
     */
    @Override
    protected boolean addMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException {
        try {
            if (type == ERROR_TYPE) {
                virtualUserTable.addErrorMapping(user, domain, mapping);
            } else if (type == REGEX_TYPE) {
                virtualUserTable.addRegexMapping(user, domain, mapping);
            } else if (type == ADDRESS_TYPE) {
                virtualUserTable.addAddressMapping(user, domain, mapping);
            } else if (type == ALIASDOMAIN_TYPE) {
                virtualUserTable.addAliasDomainMapping(domain, mapping);
            } else {
                return false;
            }
            return true;
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }

    /**
     * @see org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest#removeMapping(String, String, String, int)
     */
    @Override
    protected boolean removeMapping(String user, String domain, String mapping, int type) throws
            RecipientRewriteTableException {
        try {
            if (type == ERROR_TYPE) {
                virtualUserTable.removeErrorMapping(user, domain, mapping);
            } else if (type == REGEX_TYPE) {
                virtualUserTable.removeRegexMapping(user, domain, mapping);
            } else if (type == ADDRESS_TYPE) {
                virtualUserTable.removeAddressMapping(user, domain, mapping);
            } else if (type == ALIASDOMAIN_TYPE) {
                virtualUserTable.removeAliasDomainMapping(domain, mapping);
            } else {
                return false;
            }
            return true;
        } catch (RecipientRewriteTableException e) {
            return false;
        }
    }
}
