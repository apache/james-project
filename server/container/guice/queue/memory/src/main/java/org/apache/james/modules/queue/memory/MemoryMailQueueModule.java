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

package org.apache.james.modules.queue.memory;

import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.james.queue.memory.MemoryMailQueueFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;

public class MemoryMailQueueModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(MemoryMailQueueFactory.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends ManageableMailQueue> provideManageableMailQueueFactory(MemoryMailQueueFactory memoryMailQueueFactory) {
        return memoryMailQueueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<?> provideMailQueueFactory(MemoryMailQueueFactory memoryMailQueueFactory) {
        return memoryMailQueueFactory;
    }

    @Provides
    @Singleton
    public MailQueueFactory<? extends MailQueue> provideMailQueueFactoryGenerics(MemoryMailQueueFactory memoryMailQueueFactory) {
        return memoryMailQueueFactory;
    }
}
