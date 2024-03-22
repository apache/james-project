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

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.core.Username;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.indexer.ReIndexer;
import org.apache.james.mailbox.indexer.ReIndexer.RunningOptions;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.util.MDCBuilder;

public class ReIndexerManagement implements ReIndexerManagementMBean {

    private final TaskManager taskManager;
    private final ReIndexer reIndexer;

    @Inject
    public ReIndexerManagement(TaskManager taskManager, @Named("reindexer") ReIndexer reIndexer) {
        this.taskManager = taskManager;
        this.reIndexer = reIndexer;
    }

    @Override
    public void reIndex(String namespace, String user, String name) throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "reIndex")
                     .build()) {
            TaskId taskId = taskManager.submit(reIndexer.reIndex(new MailboxPath(namespace, Username.of(user), name), RunningOptions.DEFAULT));
            taskManager.await(taskId, Duration.of(365, ChronoUnit.DAYS));
        } catch (IOException | TaskManager.ReachedTimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reIndex() throws MailboxException {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addToContext(MDCBuilder.PROTOCOL, "CLI")
                     .addToContext(MDCBuilder.ACTION, "reIndex")
                     .build()) {
            TaskId taskId = taskManager.submit(reIndexer.reIndex(RunningOptions.DEFAULT));
            taskManager.await(taskId, Duration.of(365, ChronoUnit.DAYS));
        } catch (IOException | TaskManager.ReachedTimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
