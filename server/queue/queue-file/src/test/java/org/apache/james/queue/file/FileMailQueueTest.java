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
import org.apache.mailet.Mail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import static org.apache.james.queue.api.Mails.defaultMail;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_LOCAL;
import static org.apache.mailet.base.MailAddressFixture.SENDER;
import static org.assertj.core.api.Assertions.assertThat;

public class FileMailQueueTest implements DelayedManageableMailQueueContract {
    private static final boolean SYNC = true;

    private TemporaryFolder temporaryFolder = new TemporaryFolder();
    private FileMailQueue mailQueue;

    @BeforeEach
    public void setUp() throws Exception {
        temporaryFolder.create();
        mailQueue = new FileMailQueue(new RawMailQueueItemDecoratorFactory(), temporaryFolder.newFolder(), "test", SYNC);
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

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentClearShouldNotAlterBrowsingWhenDequeueWhileIterating() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentClearShouldNotAlterBrowsingWhenDequeue() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentDequeueShouldNotAlterBrowsing() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentDequeueShouldNotAlterBrowsingWhenDequeueWhileIterating() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentEnqueueShouldNotAlterBrowsingWhenDequeueWhileIterating() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentRemoveShouldNotAlterBrowsingWhenDequeueWhileIterating() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentFlushShouldNotAlterBrowsingWhenDequeueWhileIterating() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentFlushShouldNotAlterBrowsing() {

    }

    @Test
    @Override
    @Disabled("JAMES-2299 No snapshot isolation")
    public void concurrentRemoveShouldNotAlterBrowsingWhenDequeue() {

    }
}
