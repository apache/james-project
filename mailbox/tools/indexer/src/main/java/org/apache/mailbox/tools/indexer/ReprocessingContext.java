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

package org.apache.mailbox.tools.indexer;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.task.Task;

public class ReprocessingContext {
    private final AtomicInteger successfullyReprocessedMails;
    private final AtomicInteger failedReprocessingMails;

    public ReprocessingContext() {
        failedReprocessingMails = new AtomicInteger(0);
        successfullyReprocessedMails = new AtomicInteger(0);
    }

    public void updateAccordingToReprocessingResult(Task.Result result) {
        switch (result) {
            case COMPLETED:
                successfullyReprocessedMails.incrementAndGet();
                break;
            case PARTIAL:
                failedReprocessingMails.incrementAndGet();
                break;
        }
    }

    public int successfullyReprocessedMailCount() {
        return successfullyReprocessedMails.get();
    }

    public int failedReprocessingMailCount() {
        return failedReprocessingMails.get();
    }
}
