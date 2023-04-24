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

package org.apache.james.mailrepository.cassandra;

public interface MailRepositoryTable {
    String KEYS_TABLE_NAME = "mailRepositoryKeys";

    String REPOSITORY_NAME = "name";

    String MAIL_KEY = "mailKey";
    String MESSAGE_SIZE = "messageSize";
    String HEADER_BLOB_ID = "headerBlobId";
    String BODY_BLOB_ID = "bodyBlobId";
    String STATE = "state";
    String SENDER = "sender";
    String RECIPIENTS = "recipients";
    String ATTRIBUTES = "attributes";
    String ERROR_MESSAGE = "errorMessage";
    String REMOTE_HOST = "remoteHost";
    String REMOTE_ADDR = "remoteAddr";
    String LAST_UPDATED = "lastUpdated";
    String PER_RECIPIENT_SPECIFIC_HEADERS = "perRecipientSpecificHeaders";

    String HEADER_TYPE = "header";
    String HEADER_NAME = "headerName";
    String HEADER_VALUE = "headerValue";
}
