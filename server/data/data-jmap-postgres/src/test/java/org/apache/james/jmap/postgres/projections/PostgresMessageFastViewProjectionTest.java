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

package org.apache.james.jmap.postgres.projections;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.api.projections.MessageFastViewProjectionContract;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.postgres.PostgresMessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresMessageFastViewProjectionTest implements MessageFastViewProjectionContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresMessageFastViewProjectionModule.MODULE));

    private PostgresMessageFastViewProjection testee;
    private PostgresMessageId.Factory postgresMessageIdFactory;
    private RecordingMetricFactory metricFactory;

    @BeforeEach
    void setUp() {
        metricFactory = new RecordingMetricFactory();
        postgresMessageIdFactory = new PostgresMessageId.Factory();
        testee = new PostgresMessageFastViewProjection(postgresExtension.getPostgresExecutor(), metricFactory);
    }

    @Override
    public MessageFastViewProjection testee() {
        return testee;
    }

    @Override
    public MessageId newMessageId() {
        return postgresMessageIdFactory.generate();
    }

    @Override
    public RecordingMetricFactory metricFactory() {
        return metricFactory;
    }
}