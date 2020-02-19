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

import org.apache.james.queue.api.DelayedManageableMailQueueContract;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.api.RawMailQueueItemDecoratorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.rules.TemporaryFolder;

@Disabled("FileMailQueue is an outdated unmaintained component suffering incomplete features and is not thread safe" +
    "This includes: " +
    " - JAMES-2298 Unsupported remove management feature" +
    " - JAMES-2954 Incomplete browse implementation" +
    " - JAMES-2544 Mixing concurrent operation might lead to a deadlock and missing fields" +
    " - JAMES-2979 dequeue is not thread safe")
public class FileCacheableMailQueueTest implements DelayedManageableMailQueueContract {
    private static final boolean SYNC = true;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private FileCacheableMailQueue mailQueue;

    @BeforeEach
    public void setUp() throws Exception {
        temporaryFolder.create();
        mailQueue = new FileCacheableMailQueue(new RawMailQueueItemDecoratorFactory(), temporaryFolder.newFolder(), "test", SYNC);
    }

    @AfterEach
    void teardown() {
        temporaryFolder.delete();
    }

    @Override
    public MailQueue getMailQueue() {
        return mailQueue;
    }

    @Override
    public ManageableMailQueue getManageableMailQueue() {
        return mailQueue;
    }
}
