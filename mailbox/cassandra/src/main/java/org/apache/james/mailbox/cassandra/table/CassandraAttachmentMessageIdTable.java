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

package org.apache.james.mailbox.cassandra.table;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface CassandraAttachmentMessageIdTable {

    String TABLE_NAME = "attachmentMessageId";
    CqlIdentifier ATTACHMENT_ID_AS_UUID = CqlIdentifier.fromCql("attachmentIdAsUUID");
    CqlIdentifier ATTACHMENT_ID = CqlIdentifier.fromCql("attachmentId");
    CqlIdentifier MESSAGE_ID = CqlIdentifier.fromCql("messageId");
    CqlIdentifier[] FIELDS = { ATTACHMENT_ID_AS_UUID, ATTACHMENT_ID, MESSAGE_ID };

}
