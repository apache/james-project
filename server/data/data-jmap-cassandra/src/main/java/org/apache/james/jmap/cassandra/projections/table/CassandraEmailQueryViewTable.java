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

package org.apache.james.jmap.cassandra.projections.table;

import com.datastax.oss.driver.api.core.CqlIdentifier;

public interface CassandraEmailQueryViewTable {
    String TABLE_NAME_SENT_AT = "email_query_view_sent_at";
    String TABLE_NAME_RECEIVED_AT = "email_query_view_received_at";
    String DATE_LOOKUP_TABLE = "email_query_view_date_lookup";

    CqlIdentifier MAILBOX_ID = CqlIdentifier.fromCql("mailboxId");
    CqlIdentifier MESSAGE_ID = CqlIdentifier.fromCql("messageId");
    CqlIdentifier RECEIVED_AT = CqlIdentifier.fromCql("receivedAt");
    CqlIdentifier SENT_AT = CqlIdentifier.fromCql("sentAt");
}
