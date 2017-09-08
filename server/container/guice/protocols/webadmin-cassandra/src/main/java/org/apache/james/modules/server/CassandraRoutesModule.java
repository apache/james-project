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

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.mailbox.cassandra.mail.migration.AttachmentV2Migration;
import org.apache.james.mailbox.cassandra.mail.migration.Migration;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.CassandraMigrationRoutes;
import org.apache.james.webadmin.service.CassandraMigrationService;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class CassandraRoutesModule extends AbstractModule {
    private static final int FROM_V2_TO_V3 = 2;
    private static final int FROM_V3_TO_V4 = 3;

    @Override
    protected void configure() {
        bind(CassandraRoutesModule.class).in(Scopes.SINGLETON);
        bind(CassandraMigrationService.class).in(Scopes.SINGLETON);

        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(CassandraMigrationRoutes.class);

        MapBinder<Integer, Migration> allMigrationClazzBinder = MapBinder.newMapBinder(binder(), Integer.class, Migration.class);
        allMigrationClazzBinder.addBinding(FROM_V2_TO_V3).toInstance(() -> Migration.MigrationResult.COMPLETED);
        allMigrationClazzBinder.addBinding(FROM_V3_TO_V4).to(AttachmentV2Migration.class);

        bindConstant()
            .annotatedWith(Names.named(CassandraMigrationService.LATEST_VERSION))
            .to(CassandraSchemaVersionManager.MAX_VERSION);
    }
}
