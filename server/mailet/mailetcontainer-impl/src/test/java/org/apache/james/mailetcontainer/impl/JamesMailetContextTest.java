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

package org.apache.james.mailetcontainer.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.AbstractDomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailetcontainer.api.LocalResources;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.rrt.lib.AbstractRecipientRewriteTable;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.junit.jupiter.api.BeforeEach;

public class JamesMailetContextTest implements JamesMailetContextContract {
    @Override
    public AbstractDomainList domainList() {
        return domainList;
    }

    @Override
    public UsersRepository usersRepository() {
        return usersRepository;
    }

    @Override
    public JamesMailetContext testee() {
        return testee;
    }

    @Override
    public MailAddress mailAddress() {
        return mailAddress;
    }

    @Override
    public MailQueue spoolMailQueue() {
        return spoolMailQueue;
    }

    @Override
    public AbstractRecipientRewriteTable recipientRewriteTable() {
        return recipientRewriteTable;
    }

    private MemoryDomainList domainList;
    private MemoryUsersRepository usersRepository;
    private JamesMailetContext testee;
    private MailAddress mailAddress;
    private MailQueue spoolMailQueue;
    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws Exception {
        DNSService dnsService = null;
        domainList = spy(new MemoryDomainList());
        domainList.configure(DomainListConfiguration.DEFAULT);

        usersRepository = spy(MemoryUsersRepository.withVirtualHosting(domainList));
        recipientRewriteTable = spy(new MemoryRecipientRewriteTable());
        recipientRewriteTable.configure(new BaseHierarchicalConfiguration());
        MailQueueFactory mailQueueFactory = mock(MailQueueFactory.class);
        spoolMailQueue = mock(MailQueue.class);
        when(mailQueueFactory.createQueue(MailQueueFactory.SPOOL)).thenReturn(spoolMailQueue);

        LocalResources localResources = new LocalResourcesImpl(usersRepository, domainList, recipientRewriteTable);
        testee = new JamesMailetContext(dnsService, domainList, localResources, mailQueueFactory);
        testee.configure(new BaseHierarchicalConfiguration());
        mailAddress = new MailAddress(USERMAIL.asString());
    }
}

