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

package org.apache.james.jmap.postgres.pushsubscription;

import java.util.Set;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.change.TypeStateFactory;
import org.apache.james.jmap.api.model.TypeName;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository;
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepositoryContract;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import scala.jdk.javaapi.CollectionConverters;

class PostgresPushSubscriptionRepositoryTest implements PushSubscriptionRepositoryContract {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresPushSubscriptionModule.MODULE));

    UpdatableTickingClock clock;
    PushSubscriptionRepository pushSubscriptionRepository;

    @BeforeEach
    void setup() {
        clock = new UpdatableTickingClock(PushSubscriptionRepositoryContract.NOW());
        pushSubscriptionRepository = new PostgresPushSubscriptionRepository(clock,
            new TypeStateFactory((Set<TypeName>) CollectionConverters.asJava(PushSubscriptionRepositoryContract.TYPE_NAME_SET())),
            postgresExtension.getExecutorFactory());
    }

    @Override
    public UpdatableTickingClock clock() {
        return clock;
    }

    @Override
    public PushSubscriptionRepository testee() {
        return pushSubscriptionRepository;
    }
}
