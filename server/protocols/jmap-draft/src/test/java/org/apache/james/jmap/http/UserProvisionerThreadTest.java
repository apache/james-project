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
package org.apache.james.jmap.http;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.Before;
import org.junit.Test;

public class UserProvisionerThreadTest {
    private static final DomainList NO_DOMAIN_LIST = null;

    private UserProvisioner testee;
    private MemoryUsersRepository usersRepository;
    private MailboxSession session;

    @Before
    public void before() {
        usersRepository = MemoryUsersRepository.withoutVirtualHosting(NO_DOMAIN_LIST);
        session = MailboxSessionUtil.create(Username.of("username"));
        testee = new UserProvisioner(usersRepository, new RecordingMetricFactory());
    }

    @Test
    public void testConcurrentAccessToFilterShouldNotThrow() throws ExecutionException, InterruptedException {
        ConcurrentTestRunner
            .builder()
            .operation((threadNumber, step) -> testee.provisionUser(session))
            .threadCount(2)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }
}
