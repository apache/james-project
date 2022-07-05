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

package org.apache.james.mailbox.opensearch;

import static org.apache.james.backends.opensearch.IndexCreationFactory.ANALYZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.BOOLEAN;
import static org.apache.james.backends.opensearch.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.opensearch.IndexCreationFactory.FIELDS;
import static org.apache.james.backends.opensearch.IndexCreationFactory.FORMAT;
import static org.apache.james.backends.opensearch.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.opensearch.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.opensearch.IndexCreationFactory.LONG;
import static org.apache.james.backends.opensearch.IndexCreationFactory.NESTED;
import static org.apache.james.backends.opensearch.IndexCreationFactory.NORMALIZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.PROPERTIES;
import static org.apache.james.backends.opensearch.IndexCreationFactory.RAW;
import static org.apache.james.backends.opensearch.IndexCreationFactory.REQUIRED;
import static org.apache.james.backends.opensearch.IndexCreationFactory.ROUTING;
import static org.apache.james.backends.opensearch.IndexCreationFactory.SEARCH_ANALYZER;
import static org.apache.james.backends.opensearch.IndexCreationFactory.TYPE;
import static org.opensearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.common.xcontent.XContentBuilder;

public class MailboxMappingFactory {
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

                        .startObject(JsonMessageConstants.MESSAGE_ID)
                            .field(TYPE, KEYWORD)
                            .field(STORE, true)
                        .endObject()

                        .startObject(JsonMessageConstants.THREAD_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(JsonMessageConstants.UID)
                            .field(TYPE, LONG)
                            .field(STORE, true)
                        .endObject()

                        .startObject(JsonMessageConstants.MODSEQ)
                            .field(TYPE, LONG)
                        .endObject()

                        .startObject(JsonMessageConstants.SIZE)
                            .field(TYPE, LONG)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_ANSWERED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_DELETED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_DRAFT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_FLAGGED)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_RECENT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.IS_UNREAD)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.DATE)
                            .field(TYPE, IndexCreationFactory.DATE)
                            .field(FORMAT, "uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                        .endObject()

                        .startObject(JsonMessageConstants.SENT_DATE)
                            .field(TYPE, IndexCreationFactory.DATE)
                            .field(FORMAT, "uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                        .endObject()

                        .startObject(JsonMessageConstants.USER_FLAGS)
                            .field(TYPE, KEYWORD)
                            .field(NORMALIZER, CASE_INSENSITIVE)
                        .endObject()

                        .startObject(JsonMessageConstants.MEDIA_TYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(JsonMessageConstants.SUBTYPE)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(JsonMessageConstants.FROM)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.EMailer.NAME)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                    .field(TYPE, JsonMessageConstants.TEXT)
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

                        .startObject(JsonMessageConstants.HEADERS)
                            .field(TYPE, NESTED)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.HEADER.NAME)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(JsonMessageConstants.HEADER.VALUE)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(JsonMessageConstants.SUBJECT)
                            .field(TYPE, JsonMessageConstants.TEXT)
                            .field(ANALYZER, KEEP_MAIL_AND_URL)
                            .startObject(FIELDS)
                                .startObject(RAW)
                                    .field(TYPE, KEYWORD)
                                    .field(NORMALIZER, CASE_INSENSITIVE)
                                .endObject()
                            .endObject()
                        .endObject()

                        .startObject(JsonMessageConstants.TO)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.EMailer.NAME)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                    .field(TYPE, JsonMessageConstants.TEXT)
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

                        .startObject(JsonMessageConstants.CC)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.EMailer.NAME)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                    .field(TYPE, JsonMessageConstants.TEXT)
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

                        .startObject(JsonMessageConstants.BCC)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.EMailer.NAME)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, KEEP_MAIL_AND_URL)
                                .endObject()
                                .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                    .field(TYPE, JsonMessageConstants.TEXT)
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

                        .startObject(JsonMessageConstants.MAILBOX_ID)
                            .field(TYPE, KEYWORD)
                            .field(STORE, true)
                        .endObject()

                        .startObject(JsonMessageConstants.MIME_MESSAGE_ID)
                            .field(TYPE, KEYWORD)
                        .endObject()

                        .startObject(JsonMessageConstants.TEXT_BODY)
                            .field(TYPE, JsonMessageConstants.TEXT)
                            .field(ANALYZER, STANDARD)
                        .endObject()

                        .startObject(JsonMessageConstants.HTML_BODY)
                            .field(TYPE, JsonMessageConstants.TEXT)
                            .field(ANALYZER, STANDARD)
                        .endObject()

                        .startObject(JsonMessageConstants.HAS_ATTACHMENT)
                            .field(TYPE, BOOLEAN)
                        .endObject()

                        .startObject(JsonMessageConstants.ATTACHMENTS)
                            .startObject(PROPERTIES)
                                .startObject(JsonMessageConstants.Attachment.FILENAME)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, STANDARD)
                                .endObject()
                                .startObject(JsonMessageConstants.Attachment.TEXT_CONTENT)
                                    .field(TYPE, JsonMessageConstants.TEXT)
                                    .field(ANALYZER, STANDARD)
                                .endObject()
                                .startObject(JsonMessageConstants.Attachment.MEDIA_TYPE)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(JsonMessageConstants.Attachment.SUBTYPE)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(JsonMessageConstants.Attachment.FILE_EXTENSION)
                                    .field(TYPE, KEYWORD)
                                .endObject()
                                .startObject(JsonMessageConstants.Attachment.CONTENT_DISPOSITION)
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
