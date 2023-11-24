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

import org.apache.james.UserEntityValidator;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.lib.RecipientRewriteTableContract;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

class MemoryRecipientRewriteTableTest implements RecipientRewriteTableContract {

    AbstractRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setup() throws Exception {
        setUp();
    }

    @AfterEach
    void teardown() throws Exception {
        tearDown();
    }

    @Override
    public void createRecipientRewriteTable() throws Exception {
        MemoryDomainList domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        recipientRewriteTable.setUsersRepository(MemoryUsersRepository.withVirtualHosting(domainList));
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
    }

    @Override
    public AbstractRecipientRewriteTable virtualUserTable() {
        return recipientRewriteTable;
    }
}
