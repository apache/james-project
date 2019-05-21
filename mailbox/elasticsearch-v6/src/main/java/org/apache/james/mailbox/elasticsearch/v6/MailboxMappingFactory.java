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

package org.apache.james.mailbox.elasticsearch.v6;

import static org.apache.james.backends.es.v6.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.es.v6.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.v6.IndexCreationFactory.SNOWBALL_KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.v6.NodeMappingFactory.ANALYZER;
import static org.apache.james.backends.es.v6.NodeMappingFactory.BOOLEAN;
import static org.apache.james.backends.es.v6.NodeMappingFactory.FIELDS;
import static org.apache.james.backends.es.v6.NodeMappingFactory.FORMAT;
import static org.apache.james.backends.es.v6.NodeMappingFactory.IGNORE_ABOVE;
import static org.apache.james.backends.es.v6.NodeMappingFactory.KEYWORD;
import static org.apache.james.backends.es.v6.NodeMappingFactory.LONG;
import static org.apache.james.backends.es.v6.NodeMappingFactory.NESTED;
import static org.apache.james.backends.es.v6.NodeMappingFactory.NORMALIZER;
import static org.apache.james.backends.es.v6.NodeMappingFactory.PROPERTIES;
import static org.apache.james.backends.es.v6.NodeMappingFactory.RAW;
import static org.apache.james.backends.es.v6.NodeMappingFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.es.v6.NodeMappingFactory.SNOWBALL;
import static org.apache.james.backends.es.v6.NodeMappingFactory.SPLIT_EMAIL;
import static org.apache.james.backends.es.v6.NodeMappingFactory.TYPE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.BCC;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.CC;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.DATE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.FROM;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.HAS_ATTACHMENT;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.HTML_BODY;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_ANSWERED;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_DELETED;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_DRAFT;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_FLAGGED;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_RECENT;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.IS_UNREAD;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.MAILBOX_ID;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.MEDIA_TYPE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.MODSEQ;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.SENT_DATE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.SIZE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.SUBJECT;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.SUBTYPE;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.TEXT;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.TEXT_BODY;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.TO;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.UID;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.USERS;
import static org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.USER_FLAGS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.backends.es.v6.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.v6.json.JsonMessageConstants.EMailer;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class MailboxMappingFactory {

    private static final int MAXIMUM_TERM_LENGTH = 4096;
    private static final String STANDARD = "standard";
    private static final String STORE = "store";

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()

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

                        .startObject(MEDIA_TYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(SUBTYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(USER_FLAGS)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(FROM)
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
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
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
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
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
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
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(EMailer.NAME)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                    .startObject(FIELDS)
                                        .startObject(RAW)
                                            .field(TYPE, KEYWORD)
                                            .field(NORMALIZER, CASE_INSENSITIVE)
                                        .endObject()
                                    .endObject()
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

                        .startObject(USERS)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(TEXT_BODY)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, KEEP_MAIL_AND_URL)
                            .startObject(FIELDS)
                                .startObject(SPLIT_EMAIL)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(RAW)
                                    .field(TYPE, KEYWORD)
                                    .field(NORMALIZER, CASE_INSENSITIVE)
                                    .field(IGNORE_ABOVE, MAXIMUM_TERM_LENGTH)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(HTML_BODY)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, KEEP_MAIL_AND_URL)
                            .startObject(FIELDS)
                                .startObject(SPLIT_EMAIL)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, STANDARD)
                                    .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(RAW)
                                    .field(TYPE, KEYWORD)
                                    .field(NORMALIZER, CASE_INSENSITIVE)
                                    .field(IGNORE_ABOVE, MAXIMUM_TERM_LENGTH)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(HAS_ATTACHMENT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(TEXT)
                            .field(TYPE, TEXT)
                            .field(ANALYZER, SNOWBALL_KEEP_MAIL_AND_URL)
                            .startObject(FIELDS)
                                .startObject(SPLIT_EMAIL)
                                    .field(TYPE, TEXT)
                                    .field(ANALYZER, SNOWBALL)
                                    .field(SEARCH_ANALYZER, SNOWBALL_KEEP_MAIL_AND_URL)
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
