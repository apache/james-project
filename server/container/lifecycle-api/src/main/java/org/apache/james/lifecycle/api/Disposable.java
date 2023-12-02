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

package org.apache.james.lifecycle.api;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classes which implement this interface need some special handling on destroy.
 * So the {@link #dispose()} method need to get called
 */
public interface Disposable {

    /**
     * Dispose the object
     */
    void dispose();

    abstract class LeakAware<T extends LeakAware.Resource> implements Disposable {
        public static class Resource implements Disposable {
            private final AtomicBoolean isDisposed = new AtomicBoolean(false);
            private final Disposable cleanup;

            public Resource(Disposable cleanup) {
                this.cleanup = cleanup;
            }

            public boolean isDisposed() {
                return isDisposed.get();
            }

            @Override
            public void dispose() {
                isDisposed.set(true);
                cleanup.dispose();
            }
        }

        public static class LeakDetectorException extends RuntimeException {
            public LeakDetectorException() {
                super();
            }
        }

        public enum Level {
            NONE,
            SIMPLE,
            ADVANCED,
            TESTING;

            static Level parse(String input) {
                for (Level level : values()) {
                    if (level.name().equalsIgnoreCase(input)) {
                        return level;
                    }
                }
                throw new IllegalArgumentException(String.format("Unknown level `%s`", input));
            }
        }

        public static final ReferenceQueue<LeakAware<?>> REFERENCE_QUEUE = new ReferenceQueue<>();
        public static final ConcurrentHashMap<LeakAwareFinalizer, Boolean> REFERENCES_IN_USE = new ConcurrentHashMap<>();
        static Level LEVEL = Optional.ofNullable(System.getProperty("james.lifecycle.leak.detection.mode"))
            .map(Level::parse).orElse(Level.SIMPLE);

        public static void track() {
            Reference<?> referenceFromQueue;
            while ((referenceFromQueue = REFERENCE_QUEUE.poll()) != null) {
                if (leakDetectorEnabled()) {
                    ((LeakAwareFinalizer) referenceFromQueue).detectLeak();
                }
                referenceFromQueue.clear();
            }
        }

        private static boolean leakDetectorEnabled() {
            return LEVEL != Level.NONE;
        }

        public static boolean tracedEnabled() {
            return LEVEL == Level.ADVANCED || LEVEL == Level.TESTING;
        }

        private final T resource;
        private LeakAwareFinalizer finalizer;

        protected LeakAware(T resource) {
            this.resource = resource;
            if (leakDetectorEnabled()) {
                this.finalizer = new LeakAwareFinalizer(this, resource, REFERENCE_QUEUE);
                REFERENCES_IN_USE.put(finalizer, true);
            }
        }

        @Override
        public void dispose() {
            if (finalizer != null) {
                REFERENCES_IN_USE.remove(finalizer);
            }
            resource.dispose();
        }

        public T getResource() {
            return resource;
        }
    }

    class TraceRecord {
        private final List<StackWalker.StackFrame> stackFrames;

        TraceRecord(List<StackWalker.StackFrame> stackFrames) {
            this.stackFrames = stackFrames;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            this.stackFrames.subList(3, this.stackFrames.size())
                .forEach(stackFrame -> {
                    buf.append("\t");
                    buf.append(stackFrame.getClassName());
                    buf.append("#");
                    buf.append(stackFrame.getMethodName());
                    buf.append(":");
                    buf.append(stackFrame.getLineNumber());
                    buf.append("\n");
                });
            return buf.toString();
        }
    }

    class LeakAwareFinalizer extends PhantomReference<LeakAware<?>> {
        private static final Logger LOGGER = LoggerFactory.getLogger(LeakAwareFinalizer.class);

        private final LeakAware.Resource resource;
        private TraceRecord traceRecord;

        public LeakAwareFinalizer(LeakAware referent, LeakAware.Resource resource, ReferenceQueue<? super LeakAware<?>> q) {
            super(referent, q);
            this.resource = resource;
            if (LeakAware.tracedEnabled()) {
                traceRecord = new TraceRecord(StackWalker.getInstance().walk(s -> s.collect(Collectors.toList())));
            }
        }

        public void detectLeak() {
            switch (LeakAware.LEVEL) {
                case NONE: // nothing
                    break;
                case SIMPLE:
                case ADVANCED: {
                    if (isNotDisposed()) {
                        errorLog();
                        resource.dispose();
                        LeakAware.REFERENCES_IN_USE.remove(this);
                    }
                    break;
                }
                case TESTING: {
                    if (isNotDisposed()) {
                        errorLog();
                        throw new LeakAware.LeakDetectorException();
                    }
                }
            }
        }

        public void errorLog() {
            if (LeakAware.tracedEnabled()) {
                LOGGER.error("Leak detected! Resource {} was not released before its referent was garbage-collected. \n" +
                    "This resource was instanced at: \n{}", resource, traceRecord.toString());
            } else {
                LOGGER.error("Leak detected! Resource {} was not released before its referent was garbage-collected. \n" +
                    "Resource management needs to be reviewed: ensure to always call dispose() for disposable objects you work with. \n" +
                    "Consider enabling advanced leak detection to further identify the problem.", resource);
            }
        }

        private boolean isNotDisposed() {
            return !resource.isDisposed();
        }
    }
}
