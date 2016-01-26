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
package org.apache.james.util.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ThreadPool which use name and a counter for thread names
 */
public class NamedThreadFactory implements ThreadFactory {

    public final String name;
    private final AtomicLong count = new AtomicLong();
    private final int priority;

    public NamedThreadFactory(final String name, final int priority) {
        if (priority > Thread.MAX_PRIORITY || priority < Thread.MIN_PRIORITY) {
            throw new IllegalArgumentException("Priority must be <= " + Thread.MAX_PRIORITY + " and >=" + Thread.MIN_PRIORITY);
        }
        this.name = name;
        this.priority = priority;
    }

    public NamedThreadFactory(final String name) {
        this(name, Thread.NORM_PRIORITY);
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setName(name + "-" + count.incrementAndGet());
        t.setPriority(priority);
        return t;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "NamedTreadFactory: " + getName();
    }

}
