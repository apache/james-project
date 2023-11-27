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

import static org.apache.james.backends.opensearch.IndexCreationFactory.CASE_INSENSITIVE;
import static org.apache.james.backends.opensearch.IndexCreationFactory.KEEP_MAIL_AND_URL;
import static org.apache.james.backends.opensearch.IndexCreationFactory.KEYWORD;
import static org.apache.james.backends.opensearch.IndexCreationFactory.RAW;

import java.util.Map;

import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.client.opensearch._types.mapping.BooleanProperty;
import org.opensearch.client.opensearch._types.mapping.DateProperty;
import org.opensearch.client.opensearch._types.mapping.DynamicMapping;
import org.opensearch.client.opensearch._types.mapping.KeywordProperty;
import org.opensearch.client.opensearch._types.mapping.LongNumberProperty;
import org.opensearch.client.opensearch._types.mapping.NestedProperty;
import org.opensearch.client.opensearch._types.mapping.ObjectProperty;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.RoutingField;
import org.opensearch.client.opensearch._types.mapping.TextProperty;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;

import com.google.common.collect.ImmutableMap;

public class MailboxMappingFactory {
    private static final String STANDARD = "standard";
    private static final String SIMPLE = "simple";

    public static TypeMapping getMappingContent() {
        return new TypeMapping.Builder()
            .dynamic(DynamicMapping.Strict)
            .routing(new RoutingField.Builder()
                .required(true)
                .build())
            .properties(generateProperties())
            .build();
    }

    private static Map<String, Property> generateProperties() {
        return new ImmutableMap.Builder<String, Property>()
            .put(JsonMessageConstants.MESSAGE_ID, new Property.Builder()
                .keyword(new KeywordProperty.Builder().store(true).build())
                .build())
            .put(JsonMessageConstants.THREAD_ID, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.UID, new Property.Builder()
                .long_(new LongNumberProperty.Builder().store(true).build())
                .build())
            .put(JsonMessageConstants.MODSEQ, new Property.Builder()
                .long_(new LongNumberProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.SIZE, new Property.Builder()
                .long_(new LongNumberProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_ANSWERED, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_DELETED, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_DRAFT, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_FLAGGED, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_RECENT, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.IS_UNREAD, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.DATE, new Property.Builder()
                .date(new DateProperty.Builder()
                    .format("uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                    .build())
                .build())
            .put(JsonMessageConstants.SENT_DATE, new Property.Builder()
                .date(new DateProperty.Builder()
                    .format("uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                    .build())
                .build())
            .put(JsonMessageConstants.SAVE_DATE, new Property.Builder()
                .date(new DateProperty.Builder()
                    .format("uuuu-MM-dd'T'HH:mm:ssX||uuuu-MM-dd'T'HH:mm:ssXXX||uuuu-MM-dd'T'HH:mm:ssXXXXX")
                    .build())
                .build())
            .put(JsonMessageConstants.USER_FLAGS, new Property.Builder()
                .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                .build())
            .put(JsonMessageConstants.MEDIA_TYPE, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.SUBTYPE, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.FROM, new Property.Builder()
                .object(new ObjectProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.EMailer.NAME, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.DOMAIN, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(SIMPLE)
                                .searchAnalyzer(KEYWORD)
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.ADDRESS, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(STANDARD)
                                .searchAnalyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build()
                    ))
                    .build())
                .build())
            .put(JsonMessageConstants.HEADERS, new Property.Builder()
                .nested(new NestedProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.HEADER.NAME, new Property.Builder()
                            .keyword(new KeywordProperty.Builder().build())
                            .build(),
                        JsonMessageConstants.HEADER.VALUE, new Property.Builder()
                            .text(new TextProperty.Builder().analyzer(KEEP_MAIL_AND_URL).build())
                            .build()
                    ))
                    .build())
                .build())
            .put(JsonMessageConstants.SUBJECT, new Property.Builder()
                .text(new TextProperty.Builder()
                    .analyzer(KEEP_MAIL_AND_URL)
                    .fields(RAW, new Property.Builder()
                        .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                        .build())
                    .build())
                .build())
            .put(JsonMessageConstants.TO, new Property.Builder()
                .object(new ObjectProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.EMailer.NAME, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.DOMAIN, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(SIMPLE)
                                .searchAnalyzer(KEYWORD)
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.ADDRESS, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(STANDARD)
                                .searchAnalyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build()
                    ))
                    .build())
                .build())
            .put(JsonMessageConstants.CC, new Property.Builder()
                .object(new ObjectProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.EMailer.NAME, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.DOMAIN, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(SIMPLE)
                                .searchAnalyzer(KEYWORD)
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.ADDRESS, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(STANDARD)
                                .searchAnalyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build()
                    ))
                    .build())
                .build())
            .put(JsonMessageConstants.BCC, new Property.Builder()
                .object(new ObjectProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.EMailer.NAME, new Property.Builder()
                            .text(new TextProperty.Builder().analyzer(KEEP_MAIL_AND_URL).build())
                            .build(),
                        JsonMessageConstants.EMailer.DOMAIN, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(SIMPLE)
                                .searchAnalyzer(KEYWORD)
                                .build())
                            .build(),
                        JsonMessageConstants.EMailer.ADDRESS, new Property.Builder()
                            .text(new TextProperty.Builder()
                                .analyzer(STANDARD)
                                .searchAnalyzer(KEEP_MAIL_AND_URL)
                                .fields(RAW, new Property.Builder()
                                    .keyword(new KeywordProperty.Builder().normalizer(CASE_INSENSITIVE).build())
                                    .build())
                                .build())
                            .build()
                    ))
                    .build())
                .build())
            .put(JsonMessageConstants.MAILBOX_ID, new Property.Builder()
                .keyword(new KeywordProperty.Builder().store(true).build())
                .build())
            .put(JsonMessageConstants.MIME_MESSAGE_ID, new Property.Builder()
                .keyword(new KeywordProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.TEXT_BODY, new Property.Builder()
                .text(new TextProperty.Builder().analyzer(STANDARD).build())
                .build())
            .put(JsonMessageConstants.HTML_BODY, new Property.Builder()
                .text(new TextProperty.Builder().analyzer(STANDARD).build())
                .build())
            .put(JsonMessageConstants.HAS_ATTACHMENT, new Property.Builder()
                .boolean_(new BooleanProperty.Builder().build())
                .build())
            .put(JsonMessageConstants.ATTACHMENTS, new Property.Builder()
                .object(new ObjectProperty.Builder()
                    .properties(ImmutableMap.of(
                        JsonMessageConstants.Attachment.FILENAME, new Property.Builder()
                            .text(new TextProperty.Builder().analyzer(STANDARD).build())
                            .build(),
                        JsonMessageConstants.Attachment.TEXT_CONTENT, new Property.Builder()
                            .text(new TextProperty.Builder().analyzer(STANDARD).build())
                            .build(),
                        JsonMessageConstants.Attachment.MEDIA_TYPE, new Property.Builder()
                            .keyword(new KeywordProperty.Builder().build())
                            .build(),
                        JsonMessageConstants.Attachment.SUBTYPE, new Property.Builder()
                            .keyword(new KeywordProperty.Builder().build())
                            .build(),
                        JsonMessageConstants.Attachment.FILE_EXTENSION, new Property.Builder()
                            .keyword(new KeywordProperty.Builder().build())
                            .build(),
                        JsonMessageConstants.Attachment.CONTENT_DISPOSITION, new Property.Builder()
                            .keyword(new KeywordProperty.Builder().build())
                            .build()
                    ))
                    .build())
                .build())
            .build();
    }
}
