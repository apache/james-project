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

package org.apache.james.task;

import java.io.IOException;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.core.scheduler.Schedulers;

public class MemoryWorkQueue implements WorkQueue {
    private final TaskManagerWorker worker;
    private final Disposable subscription;
    private final UnicastProcessor<TaskWithId> tasks;

    public MemoryWorkQueue(TaskManagerWorker worker) {
        this.worker = worker;
        this.tasks = UnicastProcessor.create();
        this.subscription = tasks
            .subscribeOn(Schedulers.elastic())
            .limitRate(1)
            .concatMap(this::dispatchTaskToWorker)
            .subscribe();
    }

    private Mono<?> dispatchTaskToWorker(TaskWithId taskWithId) {
        return worker.executeTask(taskWithId);
    }

    public void submit(TaskWithId taskWithId) {
        tasks.onNext(taskWithId);
    }

    public void cancel(TaskId taskId) {
        worker.cancelTask(taskId);
    }

    @Override
    public void close() throws IOException {
        try {
            subscription.dispose();
        } catch (Throwable ignore) {
            //avoid failing during close
        }
        worker.close();
    }

}
