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

package org.apache.james.vacation.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.core.MailAddress;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.NotificationRegistryContract;
import org.apache.james.vacation.api.RecipientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresNotificationRegistryTest implements NotificationRegistryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PostgresModule.aggregateModules(PostgresVacationModule.MODULE));

    NotificationRegistry notificationRegistry;
    RecipientId recipientId;

    @BeforeEach
    public void setUp() throws Exception {
        notificationRegistry = new PostgresNotificationRegistry(zonedDateTimeProvider, postgresExtension.getExecutorFactory());
        recipientId = RecipientId.fromMailAddress(new MailAddress("benwa@apache.org"));
    }
    @Override
    public NotificationRegistry notificationRegistry() {
        return notificationRegistry;
    }

    @Override
    public RecipientId recipientId() {
        return recipientId;
    }
}
