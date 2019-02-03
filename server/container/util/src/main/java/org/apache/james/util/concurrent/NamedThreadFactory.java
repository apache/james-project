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

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class NamedThreadFactory implements ThreadFactory {

    public final String name;
    private final ThreadFactory threadFactory;

    public static NamedThreadFactory withClassName(Class<?> clazz) {
        return new NamedThreadFactory(clazz.getName());
    }

    public static NamedThreadFactory withName(String name) {
        return new NamedThreadFactory(name);
    }

    private NamedThreadFactory(String name) {
        this.name = name;
        this.threadFactory = new ThreadFactoryBuilder().setNameFormat(name + "-%d").build();
    }

    @Override
    public Thread newThread(Runnable r) {
        return threadFactory.newThread(r);
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "NamedThreadFactory: " + getName();
    }

}
