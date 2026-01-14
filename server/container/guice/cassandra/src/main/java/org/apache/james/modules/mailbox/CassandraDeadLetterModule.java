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

import static org.apache.james.events.EventDeadLettersHealthCheck.DEAD_LETTERS_IGNORED_GROUPS;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.events.CassandraEventDeadLetters;
import org.apache.james.events.CassandraEventDeadLettersDAO;
import org.apache.james.events.CassandraEventDeadLettersDataDefinition;
import org.apache.james.events.CassandraEventDeadLettersGroupDAO;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventDeadLettersHealthCheck;
import org.apache.james.events.Group;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;

public class CassandraDeadLetterModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CassandraEventDeadLettersGroupDAO.class).in(Scopes.SINGLETON);
        bind(CassandraEventDeadLettersDAO.class).in(Scopes.SINGLETON);
        bind(CassandraEventDeadLetters.class).in(Scopes.SINGLETON);

        bind(EventDeadLetters.class).to(CassandraEventDeadLetters.class);

        Multibinder.newSetBinder(binder(), CassandraDataDefinition.class)
            .addBinding()
            .toInstance(CassandraEventDeadLettersDataDefinition.MODULE);

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding()
            .to(EventDeadLettersHealthCheck.class);
        Multibinder.newSetBinder(binder(), Group.class, Names.named(DEAD_LETTERS_IGNORED_GROUPS));
    }
}
