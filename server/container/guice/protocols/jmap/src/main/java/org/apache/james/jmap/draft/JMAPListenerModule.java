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
package org.apache.james.jmap.draft;

import org.apache.james.events.EventListener;
import org.apache.james.jmap.change.MailboxChangeListener;
import org.apache.james.jmap.event.ComputeMessageFastViewProjectionListener;
import org.apache.james.jmap.event.PropagateLookupRightListener;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

public class JMAPListenerModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), EventListener.GroupEventListener.class).addBinding().to(PropagateLookupRightListener.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class).addBinding().to(MailboxChangeListener.class);
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class)
            .addBinding()
            .to(ComputeMessageFastViewProjectionListener.class);
    }
}
