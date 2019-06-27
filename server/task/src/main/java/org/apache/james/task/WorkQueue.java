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

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.google.common.collect.Sets;
import reactor.core.Disposable;
import reactor.core.publisher.WorkQueueProcessor;
import reactor.core.scheduler.Schedulers;

public class WorkQueue implements Closeable {

    public enum Status {
        STARTED,
        CANCELLED
    }

    public static class Event {
        public final TaskId id;
        public final Status status;

        private Event(TaskId id, Status status) {
            this.id = id;
            this.status = status;
        }
    }

    public static RequireWorker builder() {
        return worker -> listener -> new WorkQueue(worker, listener);
    }

    public interface RequireWorker {
        RequireListener worker(Consumer<TaskWithId> worker);
    }

    public interface RequireListener {
        WorkQueue listener(Consumer<Event> worker);
    }

    private final WorkQueueProcessor<TaskWithId> workQueue;
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService requestTaskExecutor = Executors.newSingleThreadExecutor();
    private final Set<TaskId> cancelledTasks;
    private final Consumer<Event> listener;
    private final Disposable subscription;

    private WorkQueue(Consumer<TaskWithId> worker, Consumer<Event> listener) {
        this.listener = listener;
        cancelledTasks = Sets.newConcurrentHashSet();
        workQueue = WorkQueueProcessor.<TaskWithId>builder()
            .executor(taskExecutor)
            .requestTaskExecutor(requestTaskExecutor)
            .build();
        subscription = workQueue
            .subscribeOn(Schedulers.single())
            .subscribe(dispatchNonCancelledTaskToWorker(worker));
    }

    private Consumer<TaskWithId> dispatchNonCancelledTaskToWorker(Consumer<TaskWithId> delegate) {
        return taskWithId -> {
            if (!cancelledTasks.remove(taskWithId.getId())) {
                listener.accept(new Event(taskWithId.getId(), Status.STARTED));
                delegate.accept(taskWithId);
            } else {
                listener.accept(new Event(taskWithId.getId(), Status.CANCELLED));
            }
        };
    }

    public void submit(TaskWithId taskWithId) {
        workQueue.onNext(taskWithId);
    }

    public void cancel(TaskId taskId) {
        cancelledTasks.add(taskId);
    }

    @Override
    public void close() throws IOException {
        try {
            subscription.dispose();
        } catch (Throwable ignore) {
            //avoid failing during close
        }
        try {
            workQueue.dispose();
        } catch (Throwable ignore) {
            //avoid failing during close
        }
        taskExecutor.shutdownNow();
        requestTaskExecutor.shutdownNow();
    }

}
