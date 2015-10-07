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

package org.apache.james.mailbox.elasticsearch.json;

public interface JsonMessageConstants {

    /*
    Properties defined by JMAP
     */
    String ID = "id";
    String MAILBOX_ID = "mailboxId";
    String IS_UNREAD = "isUnread";
    String IS_FLAGGED = "isFlagged";
    String IS_ANSWERED = "isAnswered";
    String IS_DRAFT = "isDraft";
    String HEADERS = "headers";
    String FROM = "from";
    String TO = "to";
    String CC = "cc";
    String BCC = "bcc";
    String SUBJECT = "subject";
    String DATE = "date";
    String SIZE = "size";
    String TEXT_BODY = "textBody";
    String SENT_DATE = "sentDate";
    String ATTACHMENTS = "attachments";

    /*
    James properties we can easily get
     */
    String PROPERTIES = "properties";
    String MODSEQ = "modSeq";
    String USER_FLAGS = "userFlags";
    String IS_RECENT = "isRecent";
    String IS_DELETED = "isDeleted";
    String MEDIA_TYPE = "mediaType";
    String SUBTYPE = "subtype";
    String HAS_ATTACHMENT = "hasAttachment";

    interface EMailer {
        String NAME = "name";
        String ADDRESS = "address";
    }

    interface Attachment {
        String TEXT_CONTENT = "textContent";
        String MEDIA_TYPE = "mediaType";
        String SUBTYPE = "subtype";
        String CONTENT_DISPOSITION = "contentDisposition";
        String FILENAME = "fileName";
        String FILE_EXTENSION = "fileExtension";
        String FILE_METADATA = "fileMetadata";
    }

    interface Property {
        String NAMESPACE = "namespace";
        String NAME = "name";
        String VALUE = "value";
    }

}
