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

import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.mail.MimeMessageStore;
import org.apache.james.queue.rabbitmq.MailQueueName;
import org.apache.james.queue.rabbitmq.view.cassandra.configuration.CassandraMailQueueViewConfiguration;

import com.datastax.oss.driver.api.core.CqlSession;


public class CassandraMailQueueViewTestFactory {

    public static CassandraMailQueueView.Factory factory(Clock clock, CqlSession session,
                                                         CassandraMailQueueViewConfiguration configuration,
                                                         MimeMessageStore.Factory mimeMessageStoreFactory) {
        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();

        EnqueuedMailsDAO enqueuedMailsDao = new EnqueuedMailsDAO(session, blobIdFactory);
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        ContentStartDAO contentStartDAO = new ContentStartDAO(session);
        DeletedMailsDAO deletedMailsDao = new DeletedMailsDAO(session);

        CassandraMailQueueBrowser cassandraMailQueueBrowser = new CassandraMailQueueBrowser(browseStartDao, deletedMailsDao, enqueuedMailsDao, mimeMessageStoreFactory, configuration, clock);
        CassandraMailQueueMailStore cassandraMailQueueMailStore = new CassandraMailQueueMailStore(enqueuedMailsDao, browseStartDao, contentStartDAO, configuration, clock);
        CassandraMailQueueMailDelete cassandraMailQueueMailDelete = new CassandraMailQueueMailDelete(deletedMailsDao, browseStartDao, contentStartDAO, enqueuedMailsDao, cassandraMailQueueBrowser, configuration, clock);

        return new CassandraMailQueueView.Factory(
            cassandraMailQueueMailStore,
            cassandraMailQueueBrowser,
            cassandraMailQueueMailDelete,
            mimeMessageStoreFactory);
    }

    public static boolean isInitialized(CqlSession session, MailQueueName mailQueueName) {
        BrowseStartDAO browseStartDao = new BrowseStartDAO(session);
        return browseStartDao.findBrowseStart(mailQueueName)
            .map(Optional::ofNullable)
            .defaultIfEmpty(Optional.empty())
            .block()
            .isPresent();
    }
}
