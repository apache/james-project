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
import java.util.concurrent.LinkedBlockingQueue;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class MemoryWorkQueue implements WorkQueue {

    private final TaskManagerWorker worker;
    private final Disposable subscription;
    private final LinkedBlockingQueue<Tuple2<TaskWithId, TaskManagerWorker.Listener>> tasks;

    public MemoryWorkQueue(TaskManagerWorker worker) {
        this.worker = worker;
        this.tasks = new LinkedBlockingQueue<>();
        this.subscription = Mono.fromCallable(tasks::take)
            .repeat()
            .subscribeOn(Schedulers.elastic())
            .flatMapSequential(this::dispatchTaskToWorker)
            .subscribe();
    }

    private Mono<?> dispatchTaskToWorker(Tuple2<TaskWithId, TaskManagerWorker.Listener> tuple) {
        TaskWithId taskWithId = tuple.getT1();
        TaskManagerWorker.Listener listener = tuple.getT2();
        return worker.executeTask(taskWithId, listener);
    }

    public void submit(TaskWithId taskWithId, TaskManagerWorker.Listener listener) {
        try {
            tasks.put(Tuples.of(taskWithId, listener));
        } catch (InterruptedException e) {
            listener.cancelled();
        }
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
