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

package org.apache.james.mailbox.postgres;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.postgres.mail.dao.PostgresThreadDAO;

public class PostgresMailboxFactory {
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    @Singleton
    public PostgresMailboxFactory(PostgresExecutor.Factory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public PostgresThreadDAO createThreadDAO(Optional<Domain> domain) {
        return new PostgresThreadDAO(executorFactory.create(domain));
    }
}
