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

package org.apache.james.domainlist.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.utils.SinglePostgresConnectionFactory;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.lib.DomainListContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.r2dbc.spi.Connection;
import reactor.core.publisher.Mono;

public class PostgresDomainListTest implements DomainListContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresDomainModule.MODULE);

    PostgresDomainList domainList;

    @BeforeEach
    public void setup() throws Exception {
        Connection connection = Mono.from(postgresExtension.getConnectionFactory().create()).block();
        domainList = new PostgresDomainList(getDNSServer("localhost"), new SinglePostgresConnectionFactory(connection));
        domainList.configure(DomainListConfiguration.builder()
            .autoDetect(false)
            .autoDetectIp(false)
            .build());
    }

    @Override
    public DomainList domainList() {
        return domainList;
    }
}
