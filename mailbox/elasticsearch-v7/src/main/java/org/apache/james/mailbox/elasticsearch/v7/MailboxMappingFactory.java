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

package org.apache.james.mailbox.elasticsearch.v7;

import static org.apache.james.backends.es.v7.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.es.v7.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.v7.NodeMappingFactory.ANALYZER;
import static org.apache.james.backends.es.v7.NodeMappingFactory.BOOLEAN;
import static org.apache.james.backends.es.v7.NodeMappingFactory.FIELDS;
import static org.apache.james.backends.es.v7.NodeMappingFactory.FORMAT;
import static org.apache.james.backends.es.v7.NodeMappingFactory.KEYWORD;
import static org.apache.james.backends.es.v7.NodeMappingFactory.LONG;
import static org.apache.james.backends.es.v7.NodeMappingFactory.NESTED;
import static org.apache.james.backends.es.v7.NodeMappingFactory.NORMALIZER;
import static org.apache.james.backends.es.v7.NodeMappingFactory.PROPERTIES;
import static org.apache.james.backends.es.v7.NodeMappingFactory.RAW;
import static org.apache.james.backends.es.v7.NodeMappingFactory.REQUIRED;
import static org.apache.james.backends.es.v7.NodeMappingFactory.ROUTING;
import static org.apache.james.backends.es.v7.NodeMappingFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.es.v7.NodeMappingFactory.TYPE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.ATTACHMENTS;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.BCC;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.CC;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.DATE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.FROM;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.HAS_ATTACHMENT;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.HEADERS;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.HTML_BODY;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_ANSWERED;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_DELETED;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_DRAFT;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_FLAGGED;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_RECENT;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.IS_UNREAD;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.MAILBOX_ID;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.MEDIA_TYPE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.MODSEQ;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.SENT_DATE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.SIZE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.SUBJECT;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.SUBTYPE;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.TEXT;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.TEXT_BODY;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.TO;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.UID;
import static org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.USER_FLAGS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.backends.es.v7.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.Attachment;
import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.EMailer;
import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants.HEADER;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class MailboxMappingFactory {

    private static final int MAXIMUM_TERM_LENGTH = 4096;
    private static final String STANDARD = "standard";
    private static final String STORE = "store";

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()

                    .field("dynamic", "strict")

                    .startObject(ROUTING)
                        .field(REQUIRED, true)
                    .endObject()

                    .startObject(PROPERTIES)

                        .startObject(MESSAGE_ID)
                            .field(TYPE, KEYWORD)
                            .field(STORE, true)
                        .endObject()

                        .startObject(UID)
                            .field(TYPE, LONG)
                            .field(STORE, true)
                        .endObject()

                        .startObject(MODSEQ)
                            .field(TYPE, LONG)
                        .endObject()

                        .startObject(SIZE)
                            .field(TYPE, LONG)
                        .endObject()

                        .startObject(IS_ANSWERED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(IS_DELETED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(IS_DRAFT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(IS_FLAGGED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(IS_RECENT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(IS_UNREAD)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(DATE)
                            .field(TYPE, NodeMappingFactory.DATE)
                            .field(FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                        .endObject()

                        .startObject(SENT_DATE)
                            .field(TYPE, NodeMappingFactory.DATE)
                            .field(FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                        .endObject()

                        .startObject(USER_FLAGS)
                            .field(TYPE, KEYWORD)
                            .field(NORMALIZER, CASE_INSENSITIVE)
                        .endObject()

                        .startObject(MEDIA_TYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(SUBTYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(FROM)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(EMailer.ADDRESS)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(HEADERS)
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(HEADER.NAME)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(HEADER.VALUE)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(SUBJECT)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, KEEP_MAIL_AND_URL)
                            .startObject(FIELDS)
                                .startObject(RAW)
                                    .field(TYPE, KEYWORD)
                                    .field(NORMALIZER, CASE_INSENSITIVE)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(TO)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(EMailer.ADDRESS)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(CC)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(EMailer.ADDRESS)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(BCC)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(EMailer.ADDRESS)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(MAILBOX_ID)
                            .field(TYPE, KEYWORD)
                            .field(STORE, true)
                        .endObject()

                        .startObject(MIME_MESSAGE_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(TEXT_BODY)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, STANDARD)
                        .endObject()

                        .startObject(HTML_BODY)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, STANDARD)
                        .endObject()

                        .startObject(HAS_ATTACHMENT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(ATTACHMENTS)
                            .startObject(PROPERTIES)
                                .startObject(Attachment.FILENAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                .endObject()
                                .startObject(Attachment.TEXT_CONTENT)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                .endObject()
                                .startObject(Attachment.MEDIA_TYPE)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(Attachment.SUBTYPE)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(Attachment.FILE_EXTENSION)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(Attachment.CONTENT_DISPOSITION)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                            .endObject()
                        .endObject()

                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
