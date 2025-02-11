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

package org.apache.james.webadmin.jettyserver;


import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

import spark.embeddedserver.VirtualThreadAware;
import spark.embeddedserver.jetty.JettyServerFactory;

/**
 * Creates Jetty Server instances.
 * Clone from spark.embeddedserver.jetty.JettyServer
 */
class JettyServer extends VirtualThreadAware.Base implements JettyServerFactory {

    /**
     * Creates a Jetty server.
     *
     * @param maxThreads          maxThreads
     * @param minThreads          minThreads
     * @param threadTimeoutMillis threadTimeoutMillis
     * @return a new jetty server instance
     */
    public Server create(int maxThreads, int minThreads, int threadTimeoutMillis) {
        final QueuedThreadPool queuedThreadPool;
        if (maxThreads > 0) {
            int max = maxThreads;
            int min = (minThreads > 0) ? minThreads : 8;
            int idleTimeout = (threadTimeoutMillis > 0) ? threadTimeoutMillis : 60000;
            queuedThreadPool = new QueuedThreadPool(max, min, idleTimeout);
        } else {
            queuedThreadPool = new QueuedThreadPool();
        }
        return create(queuedThreadPool);
    }

    /**
     * Creates a Jetty server with supplied thread pool
     * @param threadPool thread pool
     * @return a new jetty server instance
     */
    @Override
    public Server create(ThreadPool threadPool) {
        if (threadPool == null) {
            threadPool = new QueuedThreadPool();
        }
        if (useVThread && VirtualThreads.areSupported() && threadPool instanceof QueuedThreadPool) {
            ((QueuedThreadPool) threadPool).setVirtualThreadsExecutor(VirtualThreads.getDefaultVirtualThreadsExecutor());
        }
        return new Server(threadPool);
    }
}
