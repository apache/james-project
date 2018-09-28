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

package org.apache.james.queue.rabbitmq.view.cassandra;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import com.datastax.driver.core.Session;

public class CassandraMailQueueViewTestFactory {

    public static CassandraMailQueueView.Factory factory(Clock clock, ThreadLocalRandom random, Session session,
                                                         CassandraTypesProvider typesProvider,
                                                         CassandraMailQueueViewConfiguration configuration) {
        EnqueuedMailsDAO enqueuedMailsDao = new EnqueuedMailsDAO(session, CassandraUtils.WITH_DEFAULT_CONFIGURATION, typesProvider);
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        DeletedMailsDAO deletedMailsDao = new DeletedMailsDAO(session);

        CassandraMailQueueBrowser cassandraMailQueueBrowser = new CassandraMailQueueBrowser(browseStartDao, deletedMailsDao, enqueuedMailsDao, configuration, clock);
        CassandraMailQueueMailStore cassandraMailQueueMailStore = new CassandraMailQueueMailStore(enqueuedMailsDao, browseStartDao, configuration, clock);
        CassandraMailQueueMailDelete cassandraMailQueueMailDelete = new CassandraMailQueueMailDelete(deletedMailsDao, browseStartDao, cassandraMailQueueBrowser, configuration, random);

        return new CassandraMailQueueView.Factory(
            cassandraMailQueueMailStore,
            cassandraMailQueueBrowser,
            cassandraMailQueueMailDelete);
    }

    public static boolean isInitialized(Session session, MailQueueName mailQueueName) {
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        return browseStartDao.findBrowseStart(mailQueueName)
            .thenApply(Optional::isPresent)
            .join();
    }
}
