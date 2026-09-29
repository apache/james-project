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
package org.apache.james.queue.activemq;

import org.apache.commons.configuration2.BaseConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.james.queue.activemq.metric.ActiveMQMetricConfiguration;

public class ActiveMQConfiguration {
    private static final String ADJUST_USAGE_LIMITS = "adjust.usage.limits";
    private static final boolean ADJUST_USAGE_LIMITS_DEFAULT = false;

    // Artemis performance & async/batching parameters
    private static final String JOURNAL_SYNC_TRANSACTIONAL = "artemis.journal.sync.transactional";
    private static final boolean JOURNAL_SYNC_TRANSACTIONAL_DEFAULT = true;

    private static final String JOURNAL_SYNC_NON_TRANSACTIONAL = "artemis.journal.sync.non.transactional";
    private static final boolean JOURNAL_SYNC_NON_TRANSACTIONAL_DEFAULT = true;

    private static final String JOURNAL_BUFFER_TIMEOUT_NIO = "artemis.journal.buffer.timeout.nio";
    // Default 1_500_000 ns (1.5 ms) enables group commit across concurrent threads
    private static final int JOURNAL_BUFFER_TIMEOUT_NIO_DEFAULT = 1_500_000;

    private static final String JOURNAL_BUFFER_SIZE_NIO = "artemis.journal.buffer.size.nio";
    private static final int JOURNAL_BUFFER_SIZE_NIO_DEFAULT = 1024 * 1024; // 1 MB write buffer

    private static final String JOURNAL_MAX_IO_NIO = "artemis.journal.max.io.nio";
    private static final int JOURNAL_MAX_IO_NIO_DEFAULT = 500;

    private static final String ASYNC_CONNECTION_EXECUTION = "artemis.async.connection.execution";
    private static final boolean ASYNC_CONNECTION_EXECUTION_DEFAULT = true;

    private static final String CLIENT_BLOCK_ON_DURABLE_SEND = "artemis.client.block.on.durable.send";
    private static final boolean CLIENT_BLOCK_ON_DURABLE_SEND_DEFAULT = true;

    private static final String CLIENT_BLOCK_ON_ACKNOWLEDGE = "artemis.client.block.on.acknowledge";
    private static final boolean CLIENT_BLOCK_ON_ACKNOWLEDGE_DEFAULT = true;

    private final ActiveMQMetricConfiguration metricConfiguration;
    private final boolean adjustUsageLimits;
    private final boolean journalSyncTransactional;
    private final boolean journalSyncNonTransactional;
    private final int journalBufferTimeoutNIO;
    private final int journalBufferSizeNIO;
    private final int journalMaxIONIO;
    private final boolean asyncConnectionExecution;
    private final boolean clientBlockOnDurableSend;
    private final boolean clientBlockOnAcknowledge;

    public static ActiveMQConfiguration getDefault() {
        return from(new BaseConfiguration());
    }

    public static ActiveMQConfiguration from(Configuration configuration) {
        return new ActiveMQConfiguration(
            ActiveMQMetricConfiguration.from(configuration),
            configuration.getBoolean(ADJUST_USAGE_LIMITS, ADJUST_USAGE_LIMITS_DEFAULT),
            configuration.getBoolean(JOURNAL_SYNC_TRANSACTIONAL, JOURNAL_SYNC_TRANSACTIONAL_DEFAULT),
            configuration.getBoolean(JOURNAL_SYNC_NON_TRANSACTIONAL, JOURNAL_SYNC_NON_TRANSACTIONAL_DEFAULT),
            configuration.getInt(JOURNAL_BUFFER_TIMEOUT_NIO, JOURNAL_BUFFER_TIMEOUT_NIO_DEFAULT),
            configuration.getInt(JOURNAL_BUFFER_SIZE_NIO, JOURNAL_BUFFER_SIZE_NIO_DEFAULT),
            configuration.getInt(JOURNAL_MAX_IO_NIO, JOURNAL_MAX_IO_NIO_DEFAULT),
            configuration.getBoolean(ASYNC_CONNECTION_EXECUTION, ASYNC_CONNECTION_EXECUTION_DEFAULT),
            configuration.getBoolean(CLIENT_BLOCK_ON_DURABLE_SEND, CLIENT_BLOCK_ON_DURABLE_SEND_DEFAULT),
            configuration.getBoolean(CLIENT_BLOCK_ON_ACKNOWLEDGE, CLIENT_BLOCK_ON_ACKNOWLEDGE_DEFAULT));
    }

    private ActiveMQConfiguration(ActiveMQMetricConfiguration metricConfiguration,
                                  boolean adjustUsageLimits,
                                  boolean journalSyncTransactional,
                                  boolean journalSyncNonTransactional,
                                  int journalBufferTimeoutNIO,
                                  int journalBufferSizeNIO,
                                  int journalMaxIONIO,
                                  boolean asyncConnectionExecution,
                                  boolean clientBlockOnDurableSend,
                                  boolean clientBlockOnAcknowledge) {
        this.metricConfiguration = metricConfiguration;
        this.adjustUsageLimits = adjustUsageLimits;
        this.journalSyncTransactional = journalSyncTransactional;
        this.journalSyncNonTransactional = journalSyncNonTransactional;
        this.journalBufferTimeoutNIO = journalBufferTimeoutNIO;
        this.journalBufferSizeNIO = journalBufferSizeNIO;
        this.journalMaxIONIO = journalMaxIONIO;
        this.asyncConnectionExecution = asyncConnectionExecution;
        this.clientBlockOnDurableSend = clientBlockOnDurableSend;
        this.clientBlockOnAcknowledge = clientBlockOnAcknowledge;
    }

    public ActiveMQMetricConfiguration getMetricConfiguration() {
        return metricConfiguration;
    }

    public boolean isAdjustUsageLimits() {
        return adjustUsageLimits;
    }

    public boolean isJournalSyncTransactional() {
        return journalSyncTransactional;
    }

    public boolean isJournalSyncNonTransactional() {
        return journalSyncNonTransactional;
    }

    public int getJournalBufferTimeoutNIO() {
        return journalBufferTimeoutNIO;
    }

    public int getJournalBufferSizeNIO() {
        return journalBufferSizeNIO;
    }

    public int getJournalMaxIONIO() {
        return journalMaxIONIO;
    }

    public boolean isAsyncConnectionExecution() {
        return asyncConnectionExecution;
    }

    public boolean isClientBlockOnDurableSend() {
        return clientBlockOnDurableSend;
    }

    public boolean isClientBlockOnAcknowledge() {
        return clientBlockOnAcknowledge;
    }
}
