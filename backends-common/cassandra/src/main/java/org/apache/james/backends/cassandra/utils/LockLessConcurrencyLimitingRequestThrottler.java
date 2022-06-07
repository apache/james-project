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

package org.apache.james.backends.cassandra.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.oss.driver.api.core.RequestThrottlingException;
import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.throttling.RequestThrottler;
import com.datastax.oss.driver.api.core.session.throttling.Throttled;

/**
 * {@link com.datastax.oss.driver.internal.core.session.throttling.ConcurrencyLimitingRequestThrottler} variation
 * implemented in a lock free maner in order to prevent lock contention in reactive pipelines.
 *
 * See https://datastax-oss.atlassian.net/browse/JAVA-3024
 *
 * James can be configured to rely on this throttler if need be.
 */
public class LockLessConcurrencyLimitingRequestThrottler implements RequestThrottler {
    private static final Logger LOG = LoggerFactory.getLogger(LockLessConcurrencyLimitingRequestThrottler.class);

    private final String logPrefix;
    private final int maxConcurrentRequests;
    private final int maxQueueSize;
    // In flight + executing
    private final AtomicInteger concurrentRequests = new AtomicInteger(0);
    private final Queue<Throttled> queue = new ConcurrentLinkedQueue<>();
    private boolean closed;

    public LockLessConcurrencyLimitingRequestThrottler(DriverContext context) {
        this.logPrefix = context.getSessionName();
        DriverExecutionProfile config = context.getConfig().getDefaultProfile();
        this.maxConcurrentRequests =
            config.getInt(DefaultDriverOption.REQUEST_THROTTLER_MAX_CONCURRENT_REQUESTS);
        this.maxQueueSize = config.getInt(DefaultDriverOption.REQUEST_THROTTLER_MAX_QUEUE_SIZE);
        LOG.debug(
            "[{}] Initializing with maxConcurrentRequests = {}, maxQueueSize = {}",
            logPrefix,
            maxConcurrentRequests,
            maxQueueSize);
    }

    @Override
    public void register(Throttled request) {
        int requestNumber = concurrentRequests.incrementAndGet();
        if (closed) {
            LOG.trace("[{}] Rejecting request after shutdown", logPrefix);
            fail(request, "The session is shutting down");
        } else if (requestNumber < maxConcurrentRequests) {
            // We have capacity for one more concurrent request
            LOG.trace("[{}] Starting newly registered request", logPrefix);
            request.onThrottleReady(false);
        } else if (requestNumber < maxQueueSize + maxConcurrentRequests) {
            LOG.trace("[{}] Enqueuing request", logPrefix);
            queue.add(request);
        } else {
            concurrentRequests.decrementAndGet();
            LOG.trace("[{}] Rejecting request because of full queue", logPrefix);
            fail(
                request,
                String.format(
                    "The session has reached its maximum capacity "
                        + "(concurrent requests: %d, queue size: %d)",
                    maxConcurrentRequests, maxQueueSize));
        }
    }

    @Override
    public void signalSuccess(Throttled request) {
        onRequestDone();
    }

    @Override
    public void signalError(Throttled request, Throwable error) {
        signalSuccess(request); // not treated differently
    }

    @Override
    public void signalTimeout(Throttled request) {
        if (!closed) {
            if (queue.remove(request)) { // The request timed out before it was active
                concurrentRequests.decrementAndGet();
                LOG.trace("[{}] Removing timed out request from the queue", logPrefix);
            } else {
                onRequestDone();
            }
        }
    }

    private void onRequestDone() {
        if (!closed) {
            concurrentRequests.decrementAndGet();
            Throttled throttled = queue.poll();
            if (throttled != null) {
                LOG.trace("[{}] Starting dequeued request", logPrefix);
                throttled.onThrottleReady(true);
                // don't touch concurrentRequests since we finished one but started another
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        LOG.debug("[{}] Rejecting {} queued requests after shutdown", logPrefix, queue.size());
        for (Throttled request : queue) {
            fail(request, "The session is shutting down");
        }
    }

    public int getQueueSize() {
        return Math.max(0, concurrentRequests.get() - maxConcurrentRequests);
    }

    private static void fail(Throttled request, String message) {
        request.onThrottleFailure(new RequestThrottlingException(message));
    }
}
