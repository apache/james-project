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

package org.apache.james.mailbox.elasticsearch;

import static org.apache.james.backends.es.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.es.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.IndexCreationFactory.SNOWBALL_KEEP_MAIL_AND_URL;
import static org.apache.james.backends.es.NodeMappingFactory.ANALYZER;
import static org.apache.james.backends.es.NodeMappingFactory.BOOLEAN;
import static org.apache.james.backends.es.NodeMappingFactory.FIELDS;
import static org.apache.james.backends.es.NodeMappingFactory.FORMAT;
import static org.apache.james.backends.es.NodeMappingFactory.IGNORE_ABOVE;
import static org.apache.james.backends.es.NodeMappingFactory.INDEX;
import static org.apache.james.backends.es.NodeMappingFactory.LONG;
import static org.apache.james.backends.es.NodeMappingFactory.NESTED;
import static org.apache.james.backends.es.NodeMappingFactory.NOT_ANALYZED;
import static org.apache.james.backends.es.NodeMappingFactory.PROPERTIES;
import static org.apache.james.backends.es.NodeMappingFactory.RAW;
import static org.apache.james.backends.es.NodeMappingFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.es.NodeMappingFactory.SNOWBALL;
import static org.apache.james.backends.es.NodeMappingFactory.SPLIT_EMAIL;
import static org.apache.james.backends.es.NodeMappingFactory.STRING;
import static org.apache.james.backends.es.NodeMappingFactory.TYPE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.BCC;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.CC;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.DATE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.FROM;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.HAS_ATTACHMENT;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.HTML_BODY;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_ANSWERED;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_DELETED;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_DRAFT;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_FLAGGED;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_RECENT;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.IS_UNREAD;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.MAILBOX_ID;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.MEDIA_TYPE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.MIME_MESSAGE_ID;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.MODSEQ;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.SENT_DATE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.SIZE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.SUBJECT;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.SUBTYPE;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.TEXT;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.TEXT_BODY;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.TO;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.UID;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.USERS;
import static org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.USER_FLAGS;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.EMailer;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants.Property;
import org.elasticsearch.common.xcontent.XContentBuilder;

public class MailboxMappingFactory {

    private static final int MAXIMUM_TERM_LENGTH = 4096;
    private static final String STANDARD = "standard";

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()

                    .startObject(MailboxElasticSearchConstants.MESSAGE_TYPE.getValue())
                        .startObject(PROPERTIES)

                            .startObject(MESSAGE_ID)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(UID)
                                .field(TYPE, LONG)
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
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(SUBTYPE)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(USER_FLAGS)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(FROM)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(EMailer.NAME)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(SUBJECT)
                                .field(TYPE, STRING)
                                .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .startObject(FIELDS)
                                    .startObject(RAW)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, CASE_INSENSITIVE)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(TO)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(EMailer.NAME)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(CC)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(EMailer.NAME)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                            .field(TYPE, STRING)
                                            .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(BCC)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(EMailer.NAME)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                        .startObject(FIELDS)
                                            .startObject(RAW)
                                                .field(TYPE, STRING)
                                                .field(ANALYZER, CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(MAILBOX_ID)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(MIME_MESSAGE_ID)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(USERS)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(PROPERTIES)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(Property.NAMESPACE)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                    .startObject(Property.NAME)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                    .startObject(Property.VALUE)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(TEXT_BODY)
                                .field(TYPE, STRING)
                                .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .startObject(FIELDS)
                                    .startObject(SPLIT_EMAIL)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .endObject()
                                    .startObject(RAW)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, CASE_INSENSITIVE)
                                        .field(IGNORE_ABOVE, MAXIMUM_TERM_LENGTH)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(HTML_BODY)
                                .field(TYPE, STRING)
                                .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .startObject(FIELDS)
                                    .startObject(SPLIT_EMAIL)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, STANDARD)
                                        .field(SEARCH_ANALYZER, KEEP_MAIL_AND_URL)
                                    .endObject()
                                    .startObject(RAW)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, CASE_INSENSITIVE)
                                        .field(IGNORE_ABOVE, MAXIMUM_TERM_LENGTH)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(HAS_ATTACHMENT)
                                .field(TYPE, BOOLEAN)
                            .endObject()

                            .startObject(TEXT)
                                .field(TYPE, STRING)
                                .field(ANALYZER, SNOWBALL_KEEP_MAIL_AND_URL)
                                .field(IGNORE_ABOVE, MAXIMUM_TERM_LENGTH)
                                .startObject(FIELDS)
                                    .startObject(SPLIT_EMAIL)
                                        .field(TYPE, STRING)
                                        .field(ANALYZER, SNOWBALL)
                                        .field(SEARCH_ANALYZER, SNOWBALL_KEEP_MAIL_AND_URL)
                                    .endObject()
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
