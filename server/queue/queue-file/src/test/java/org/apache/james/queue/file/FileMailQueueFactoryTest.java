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

package org.apache.james.queue.file;

import org.apache.james.filesystem.api.mock.MockFileSystem;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.MailQueueFactoryContract;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.ManageableMailQueueFactoryContract;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

@Disabled("FileMailQueue is an outdated unmaintained component suffering incomplete features and is not thread safe" +
    "This includes: " +
    " - JAMES-2298 Unsupported remove management feature" +
    " - JAMES-2954 Incomplete browse implementation" +
    " - JAMES-2544 Mixing concurrent operation might lead to a deadlock and missing fields" +
    " - JAMES-2979 dequeue is not thread safe")
public class FileMailQueueFactoryTest implements MailQueueFactoryContract<ManageableMailQueue>, ManageableMailQueueFactoryContract {
    private FileMailQueueFactory mailQueueFactory;
    private MockFileSystem fileSystem;

    @BeforeEach
    public void setUp() throws Exception {
        fileSystem = new MockFileSystem();
        mailQueueFactory = new FileMailQueueFactory(fileSystem, new RawMailQueueItemDecoratorFactory());
    }

    @AfterEach
    void teardown() {
        fileSystem.clear();
    }

    @Override
    public MailQueueFactory<ManageableMailQueue> getMailQueueFactory() {
        return mailQueueFactory;
    }
}