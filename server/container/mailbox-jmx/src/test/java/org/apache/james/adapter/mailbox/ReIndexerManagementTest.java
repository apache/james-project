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

package org.apache.james.adapter.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ReIndexerManagementTest {
    private ReIndexerManagement testee;
    private TaskManager taskManager;
    private ReIndexer reIndexer;

    @BeforeEach
    void setUp() {
        taskManager = new MemoryTaskManager();
        reIndexer = mock(ReIndexer.class);
        testee = new ReIndexerManagement(taskManager, reIndexer);
    }

    @Test
    void reIndexMailboxShouldWaitsForExecution() throws MailboxException {
        Task task = mock(Task.class);
        String namespace = "namespace";
        String user = "user";
        String name = "name";
        when(reIndexer.reIndex(any(MailboxPath.class))).thenReturn(task);

        assertThat(taskManager.list()).isEmpty();
        testee.reIndex(namespace, user, name);
        verify(reIndexer).reIndex(new MailboxPath(namespace, user, name));
        assertThat(taskManager.list()).hasSize(1);
    }

    @Test
    void reIndexShouldWaitsForExecution() throws MailboxException {
        Task task = mock(Task.class);
        when(reIndexer.reIndex()).thenReturn(task);

        assertThat(taskManager.list()).isEmpty();
        testee.reIndex();
        verify(reIndexer).reIndex();
        assertThat(taskManager.list()).hasSize(1);
    }
}
