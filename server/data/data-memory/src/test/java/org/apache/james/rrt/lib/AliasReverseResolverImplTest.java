/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ***************************************************************/

package org.apache.james.rrt.lib;

import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.rrt.api.AliasReverseResolver;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;

public class AliasReverseResolverImplTest implements AliasReverseResolverContract {

    AbstractRecipientRewriteTable recipientRewriteTable;
    AliasReverseResolverImpl aliasReverseResolver;

    @BeforeEach
    void setup() throws Exception {
        recipientRewriteTable = new MemoryRecipientRewriteTable();

        MemoryDomainList domainList = new MemoryDomainList();
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(DOMAIN);
        domainList.addDomain(OTHER_DOMAIN);
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setUsersRepository(MemoryUsersRepository.withVirtualHosting(domainList));
        recipientRewriteTable.setUserEntityValidator(UserEntityValidator.NOOP);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);

        this.aliasReverseResolver = new AliasReverseResolverImpl(recipientRewriteTable);
    }

    @Override
    public AliasReverseResolver aliasReverseResolver() {
        return aliasReverseResolver;
    }

    @Override
    public void addAliasMapping(Username alias, Username user) throws Exception {
        recipientRewriteTable.addAliasMapping(MappingSource.fromUser(alias.getLocalPart(), alias.getDomainPart().get()), user.asString());
    }

    @Override
    public void addDomainAlias(Domain alias, Domain domain) throws Exception {
        recipientRewriteTable.addDomainAliasMapping(MappingSource.fromDomain(alias), domain);
    }

    @Override
    public void addGroupMapping(String group, Username user) throws Exception {
        recipientRewriteTable.addGroupMapping(MappingSource.fromUser(Username.of(group)), user.asString());
    }
}
