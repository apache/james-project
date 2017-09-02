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

package org.apache.james.onami.lifecycle;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link Stager} implementation.
 */
public class DefaultStager<A extends Annotation> implements DisposingStager<A> {
    private final Class<A> stage;

    /**
     * Stack of elements have to be disposed.
     */
    private final Queue<Stageable> stageables;

    /**
     * @param stage the annotation that specifies this stage
     */
    public DefaultStager(Class<A> stage) {
        this(stage, Order.FIRST_IN_FIRST_OUT);
    }

    /**
     * @param stage the annotation that specifies this stage
     * @param mode  execution order
     */
    public DefaultStager(Class<A> stage, Order mode) {
        this.stage = stage;

        Queue<Stageable> localStageables;
        switch (mode) {
            case FIRST_IN_FIRST_OUT: {
                localStageables = new ArrayDeque<>();
                break;
            }

            case FIRST_IN_LAST_OUT: {
                localStageables = Collections.asLifoQueue(new ArrayDeque<Stageable>());
                break;
            }

            default: {
                throw new IllegalArgumentException("Unknown mode: " + mode);
            }
        }
        stageables = localStageables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(Stageable stageable) {
        synchronized (stageables) {
            stageables.add(stageable);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends ExecutorService> T register(T executorService) {
        register(new ExecutorServiceStageable(executorService));
        return executorService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends Closeable> T register(T closeable) {
        register(new CloseableStageable(closeable));
        return closeable;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stage() {
        stage(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stage(StageHandler stageHandler) {
        if (stageHandler == null) {
            stageHandler = new NoOpStageHandler();
        }

        while (true) {
            Stageable stageable;
            synchronized (stageables) {
                stageable = stageables.poll();
            }
            if (stageable == null) {
                break;
            }
            stageable.stage(stageHandler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<A> getStage() {
        return stage;
    }

    /**
     * specifies ordering for a {@link DefaultStager}
     */
    public static enum Order {
        /**
         * FIFO
         */
        FIRST_IN_FIRST_OUT,

        /**
         * FILO/LIFO
         */
        FIRST_IN_LAST_OUT
    }

    private static class CloseableStageable extends AbstractStageable<Closeable> {

        public CloseableStageable(Closeable closeable) {
            super(closeable);
        }

        @Override
        protected void doStage() throws Exception {
            object.close();
        }

    }

    private static class ExecutorServiceStageable extends AbstractStageable<ExecutorService> {

        public ExecutorServiceStageable(ExecutorService executor) {
            super(executor);
        }

        @Override
        protected void doStage() throws Exception {
            object.shutdown();
            try {
                if (!object.awaitTermination(1, TimeUnit.MINUTES)) {
                    object.shutdownNow();
                }
            } catch (InterruptedException e) {
                object.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

    }

}
