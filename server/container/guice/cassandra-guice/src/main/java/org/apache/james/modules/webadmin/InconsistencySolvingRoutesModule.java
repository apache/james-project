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

package org.apache.james.modules.webadmin;

import org.apache.james.mailbox.cassandra.mail.task.RecomputeMailboxCountersService;
import org.apache.james.mailbox.cassandra.mail.task.SolveMailboxInconsistenciesService;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.CassandraMappingsRoutes;
import org.apache.james.webadmin.routes.MailboxesRoutes;
import org.apache.james.webadmin.routes.RecomputeMailboxCountersRequestToTask;
import org.apache.james.webadmin.routes.SolveMailboxInconsistenciesRequestToTask;
import org.apache.james.webadmin.service.CassandraMappingsService;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class InconsistencySolvingRoutesModule extends AbstractModule {
    public static class SolveRRTInconsistenciesModules extends AbstractModule {
        @Override
        protected void configure() {
            bind(CassandraMappingsRoutes.class).in(Scopes.SINGLETON);
            bind(CassandraMappingsService.class).in(Scopes.SINGLETON);

            Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
            routesMultibinder.addBinding().to(CassandraMappingsRoutes.class);
        }
    }

    public static class SolveMailboxInconsistenciesModules extends AbstractModule {
        @Override
        protected void configure() {
            bind(RecomputeMailboxCountersService.class).in(Scopes.SINGLETON);
            bind(SolveMailboxInconsistenciesService.class).in(Scopes.SINGLETON);

            Multibinder<TaskFromRequestRegistry.TaskRegistration> multiBinder = Multibinder.newSetBinder(binder(),
                TaskFromRequestRegistry.TaskRegistration.class, Names.named(MailboxesRoutes.ALL_MAILBOXES_TASKS));

            multiBinder.addBinding().to(SolveMailboxInconsistenciesRequestToTask.class);
            multiBinder.addBinding().to(RecomputeMailboxCountersRequestToTask.class);
        }
    }

    @Override
    protected void configure() {
        install(new SolveRRTInconsistenciesModules());
        install(new SolveMailboxInconsistenciesModules());
    }
}
