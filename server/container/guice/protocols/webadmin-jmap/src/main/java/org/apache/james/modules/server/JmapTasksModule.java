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

package org.apache.james.modules.server;

import org.apache.james.webadmin.data.jmap.PopulateEmailQueryViewRequestToTask;
import org.apache.james.webadmin.data.jmap.PopulateFilteringProjectionRequestToTask;
import org.apache.james.webadmin.data.jmap.RecomputeAllFastViewProjectionItemsRequestToTask;
import org.apache.james.webadmin.data.jmap.RecomputeUserFastViewProjectionItemsRequestToTask;
import org.apache.james.webadmin.routes.MailboxesRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class JmapTasksModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new JmapTaskSerializationModule());

        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(MailboxesRoutes.ALL_MAILBOXES_TASKS))
            .addBinding().to(RecomputeAllFastViewProjectionItemsRequestToTask.class);

        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(MailboxesRoutes.ALL_MAILBOXES_TASKS))
            .addBinding().to(PopulateEmailQueryViewRequestToTask.class);

        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(MailboxesRoutes.ALL_MAILBOXES_TASKS))
            .addBinding().to(PopulateFilteringProjectionRequestToTask.class);

        Multibinder.newSetBinder(binder(), TaskFromRequestRegistry.TaskRegistration.class, Names.named(UserMailboxesRoutes.USER_MAILBOXES_OPERATIONS_INJECTION_KEY))
            .addBinding().to(RecomputeUserFastViewProjectionItemsRequestToTask.class);

    }
}
