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

package org.apache.james.modules.mailbox;

import static org.apache.james.mailbox.postgres.DeleteMessageListener.CONTENT_DELETION;

import java.util.Set;

import jakarta.inject.Named;

import org.apache.james.events.EventBus;
import org.apache.james.events.EventListener;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;

public class PostgresMemoryContentDeletionEventBusModule extends AbstractModule {
    public static class ContentDeletionListenersLoader implements Startable {

    }

    @Override
    protected void configure() {
        bind(EventBus.class).annotatedWith(Names.named(CONTENT_DELETION)).to(EventBus.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(CONTENT_DELETION));
    }

    @ProvidesIntoSet
    public InitializationOperation registerListener(@Named(CONTENT_DELETION) EventBus contentDeletionEventBus,
                                                    @Named(CONTENT_DELETION) Set<EventListener.ReactiveGroupEventListener> contentDeletionListeners) {
        return InitilizationOperationBuilder
            .forClass(ContentDeletionListenersLoader.class)
            .init(() -> contentDeletionListeners.forEach(contentDeletionEventBus::register));
    }
}
