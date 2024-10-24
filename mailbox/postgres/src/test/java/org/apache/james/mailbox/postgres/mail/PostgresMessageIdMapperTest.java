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

package org.apache.james.mailbox.postgres.mail;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.mailbox.postgres.PostgresMailboxAggregateModule;
import org.apache.james.mailbox.store.mail.model.MapperProvider;
import org.apache.james.mailbox.store.mail.model.MessageIdMapperTest;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.extension.RegisterExtension;

public class PostgresMessageIdMapperTest extends MessageIdMapperTest {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresMailboxAggregateModule.MODULE);

    private PostgresMapperProvider postgresMapperProvider;

    @Override
    protected MapperProvider provideMapper() {
        postgresMapperProvider = new PostgresMapperProvider(postgresExtension);
        return postgresMapperProvider;
    }

    @Override
    protected UpdatableTickingClock updatableTickingClock() {
        return postgresMapperProvider.getUpdatableTickingClock();
    }
}
