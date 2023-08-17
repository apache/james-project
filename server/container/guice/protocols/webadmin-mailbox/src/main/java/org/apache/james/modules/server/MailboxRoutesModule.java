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

import static org.apache.james.webadmin.routes.MailboxesRoutes.ALL_MAILBOXES_TASKS;
import static org.apache.james.webadmin.routes.MailboxesRoutes.ONE_MAILBOX_TASKS;
import static org.apache.james.webadmin.routes.MailboxesRoutes.ONE_MAIL_TASKS;
import static org.apache.james.webadmin.routes.UserMailboxesRoutes.USER_MAILBOXES_OPERATIONS_INJECTION_KEY;
import static org.apache.james.webadmin.routes.UserQuotaRoutes.USER_QUOTAS_OPERATIONS_INJECTION_KEY;

import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.routes.DomainQuotaRoutes;
import org.apache.james.webadmin.routes.EventDeadLettersRoutes;
import org.apache.james.webadmin.routes.GlobalQuotaRoutes;
import org.apache.james.webadmin.routes.MailboxesRoutes;
import org.apache.james.webadmin.routes.QuotaRoutes;
import org.apache.james.webadmin.routes.UserMailboxesRoutes;
import org.apache.james.webadmin.routes.UserQuotaRoutes;
import org.apache.james.webadmin.service.CreateMissingParentsRequestToTask;
import org.apache.james.webadmin.service.SubscribeAllRequestToTask;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry.TaskRegistration;
import org.apache.james.webadmin.utils.JsonTransformerModule;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class MailboxRoutesModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new WebadminMailboxTaskSerializationModule());

        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(DomainQuotaRoutes.class);
        routesMultibinder.addBinding().to(EventDeadLettersRoutes.class);
        routesMultibinder.addBinding().to(GlobalQuotaRoutes.class);
        routesMultibinder.addBinding().to(UserQuotaRoutes.class);
        routesMultibinder.addBinding().to(QuotaRoutes.class);
        routesMultibinder.addBinding().to(UserMailboxesRoutes.class);
        routesMultibinder.addBinding().to(MailboxesRoutes.class);

        Multibinder<JsonTransformerModule> jsonTransformerModuleMultibinder = Multibinder.newSetBinder(binder(), JsonTransformerModule.class);
        jsonTransformerModuleMultibinder.addBinding().to(QuotaModule.class);

        Multibinder<TaskRegistration> userBoundTasks = Multibinder.newSetBinder(binder(), TaskRegistration.class,
            Names.named(USER_MAILBOXES_OPERATIONS_INJECTION_KEY));
        Multibinder<TaskRegistration> allMailboxesTasks = Multibinder.newSetBinder(binder(), TaskRegistration.class, Names.named(ALL_MAILBOXES_TASKS));
        Multibinder.newSetBinder(binder(), TaskRegistration.class, Names.named(ONE_MAILBOX_TASKS));
        Multibinder.newSetBinder(binder(), TaskRegistration.class, Names.named(ONE_MAIL_TASKS));
        Multibinder.newSetBinder(binder(), TaskRegistration.class, Names.named(USER_QUOTAS_OPERATIONS_INJECTION_KEY));

        userBoundTasks.addBinding().to(SubscribeAllRequestToTask.class);
        allMailboxesTasks.addBinding().to(CreateMissingParentsRequestToTask.class);
    }
}
