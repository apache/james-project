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

package org.apache.james.mailbox.postgres.user;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionDAO;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionMapper;
import org.apache.james.mailbox.postgres.user.PostgresSubscriptionModule;
import org.apache.james.mailbox.store.user.SubscriptionMapper;
import org.apache.james.mailbox.store.user.SubscriptionMapperTest;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresSubscriptionMapperTest extends SubscriptionMapperTest {

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresSubscriptionModule.MODULE);

    @Override
    protected SubscriptionMapper createSubscriptionMapper() {
        PostgresSubscriptionDAO dao = new PostgresSubscriptionDAO(postgresExtension.getPostgresExecutor());
        return new PostgresSubscriptionMapper(dao);
    }
}
