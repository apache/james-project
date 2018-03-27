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

package org.apache.james.rrt.memory;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.core.Domain;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTableTest;
import org.apache.james.rrt.lib.Mapping.Type;
import org.junit.After;
import org.junit.Before;

public class MemoryRecipientRewriteTableTest extends AbstractRecipientRewriteTableTest {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected AbstractRecipientRewriteTable getRecipientRewriteTable() throws Exception {
        AbstractRecipientRewriteTable rrt = new MemoryRecipientRewriteTable();
        rrt.configure(new DefaultConfigurationBuilder());
        return rrt;
    }

    @Override
    protected void addMapping(String user, Domain domain, String mapping, Type type) throws RecipientRewriteTableException {
        switch (type) {
        case Error:
            virtualUserTable.addErrorMapping(user, domain, mapping);
            break;
        case Regex:
            virtualUserTable.addRegexMapping(user, domain, mapping);
            break;
        case Address:
            virtualUserTable.addAddressMapping(user, domain, mapping);
            break;
        case Domain:
            virtualUserTable.addAliasDomainMapping(domain, Domain.of(mapping));
            break;
        default:
            throw new RuntimeException("Invalid mapping type: " + type);
        }
    }

    @Override
    protected void removeMapping(String user, Domain domain, String mapping, Type type) throws RecipientRewriteTableException {
        switch (type) {
        case Error:
            virtualUserTable.removeErrorMapping(user, domain, mapping);
            break;
        case Regex:
            virtualUserTable.removeRegexMapping(user, domain, mapping);
            break;
        case Address:
            virtualUserTable.removeAddressMapping(user, domain, mapping);
            break;
        case Domain:
            virtualUserTable.removeAliasDomainMapping(domain, Domain.of(mapping));
            break;
        default:
            throw new RuntimeException("Invalid mapping type: " + type);
        }
    }
}
