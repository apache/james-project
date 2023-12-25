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

package org.apache.james.modules.events;

import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventDeadLettersHealthCheck;
import org.apache.james.events.PostgresEventDeadLetters;
import org.apache.james.events.PostgresEventDeadLettersModule;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;

public class PostgresDeadLetterModule extends AbstractModule {
    @Override
    protected void configure() {
        Multibinder.newSetBinder(binder(), PostgresModule.class)
            .addBinding().toInstance(PostgresEventDeadLettersModule.MODULE);

        bind(PostgresEventDeadLetters.class).in(Scopes.SINGLETON);
        bind(EventDeadLetters.class).to(PostgresEventDeadLetters.class);

        bind(EventDeadLettersHealthCheck.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(EventDeadLettersHealthCheck.class);
    }
}
