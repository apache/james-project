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

package org.apache.james.mailbox.cassandra.mail.migration;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.migration.Migration;
import org.apache.james.mailbox.cassandra.mail.CassandraAttachmentMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageDAO.MessageIdAttachmentIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttachmentMessageIdCreation implements Migration {
    private static final Logger LOGGER = LoggerFactory.getLogger(AttachmentMessageIdCreation.class);
    private final CassandraMessageDAO cassandraMessageDAO;
    private final CassandraAttachmentMessageIdDAO attachmentMessageIdDAO;

    @Inject
    public AttachmentMessageIdCreation(CassandraMessageDAO cassandraMessageDAO,
                                 CassandraAttachmentMessageIdDAO attachmentMessageIdDAO) {
        this.cassandraMessageDAO = cassandraMessageDAO;
        this.attachmentMessageIdDAO = attachmentMessageIdDAO;
    }

    @Override
    public Result run() {
        try {
            return cassandraMessageDAO.retrieveAllMessageIdAttachmentIds()
                .join()
                .map(this::createIndex)
                .reduce(Result.COMPLETED, Migration::combine);
        } catch (Exception e) {
            LOGGER.error("Error while creation attachmentId -> messageIds index", e);
            return Result.PARTIAL;
        }
    }

    private Result createIndex(MessageIdAttachmentIds message) {
        try {
            message.getAttachmentId()
                .forEach(attachmentId -> attachmentMessageIdDAO
                    .storeAttachmentForMessageId(attachmentId, message.getMessageId())
                    .join());
            return Result.COMPLETED;
        } catch (Exception e) {
            LOGGER.error("Error while creation attachmentId -> messageIds index", e);
            return Result.PARTIAL;
        }
    }
}
