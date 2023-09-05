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

import org.apache.james.backends.cassandra.migration.CassandraMigrationService;
import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.backends.cassandra.migration.MigrationTask;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaTransition;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.mailbox.cassandra.quota.CassandraPerUserMaxQuotaManagerMigration;
import org.apache.james.mailbox.cassandra.quota.migration.CassandraCurrentQuotaManagerMigration;
import org.apache.james.sieve.cassandra.migration.SieveQuotaMigration;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.routes.CassandraMailboxMergingRoutes;
import org.apache.james.webadmin.routes.CassandraMigrationRoutes;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class CassandraRoutesModule extends AbstractModule {
    private static final SchemaTransition FROM_V12_TO_V13 = SchemaTransition.to(new SchemaVersion(13));
    private static final SchemaTransition FROM_V13_TO_V14 = SchemaTransition.to(new SchemaVersion(14));

    @Override
    protected void configure() {
        bind(MigrationTask.Impl.class).in(Scopes.SINGLETON);
        bind(CassandraRoutesModule.class).in(Scopes.SINGLETON);
        bind(CassandraMailboxMergingRoutes.class).in(Scopes.SINGLETON);
        bind(CassandraMigrationService.class).in(Scopes.SINGLETON);

        bind(MigrationTask.Factory.class).to(MigrationTask.Impl.class);

        Multibinder<Routes> routesMultibinder = Multibinder.newSetBinder(binder(), Routes.class);
        routesMultibinder.addBinding().to(CassandraMigrationRoutes.class);
        routesMultibinder.addBinding().to(CassandraMailboxMergingRoutes.class);

        MapBinder<SchemaTransition, Migration> allMigrationClazzBinder = MapBinder.newMapBinder(binder(), SchemaTransition.class, Migration.class);
        allMigrationClazzBinder.addBinding(FROM_V12_TO_V13).to(CassandraCurrentQuotaManagerMigration.class);
        allMigrationClazzBinder.addBinding(FROM_V12_TO_V13).to(CassandraPerUserMaxQuotaManagerMigration.class);
        allMigrationClazzBinder.addBinding(FROM_V13_TO_V14).to(SieveQuotaMigration.class);

        bind(SchemaVersion.class)
            .annotatedWith(Names.named(CassandraMigrationService.LATEST_VERSION))
            .toInstance(CassandraSchemaVersionManager.MAX_VERSION);
    }
}
