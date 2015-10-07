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

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;

import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.xcontent.XContentBuilder;

import com.google.common.base.Throwables;

public class NodeMappingFactory {

    public static final String BOOLEAN = "boolean";
    public static final String TYPE = "type";
    public static final String LONG = "long";
    public static final String INDEX = "index";
    public static final String NOT_ANALYZED = "not_analyzed";
    public static final String STRING = "string";
    public static final String PROPERTIES = "properties";
    public static final String DATE = "date";
    public static final String FORMAT = "format";
    public static final String NESTED = "nested";

    public static ClientProvider applyMapping(ClientProvider clientProvider) {
        try (Client client = clientProvider.get()) {
            client.admin()
                .indices()
                .preparePutMapping(ElasticSearchIndexer.MAILBOX_INDEX)
                .setType(ElasticSearchIndexer.MESSAGE_TYPE)
                .setSource(getMappingContent())
                .execute()
                .actionGet();
        }
        return clientProvider;
    }

    private static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()
                    .startObject(ElasticSearchIndexer.MESSAGE_TYPE)
                        .startObject(PROPERTIES)
                            .startObject(JsonMessageConstants.ID)
                                .field(TYPE, LONG)
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
                                .field(TYPE, DATE)
                                .field(FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                            .endObject()
                            .startObject(JsonMessageConstants.SENT_DATE)
                                .field(TYPE, DATE)
                                .field(FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                            .endObject()
                            .startObject(JsonMessageConstants.MEDIA_TYPE)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.SUBTYPE)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.USER_FLAGS)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()

                            .startObject(JsonMessageConstants.FROM)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(TYPE, STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.TO)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(TYPE, STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.CC)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(TYPE, STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.BCC)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(TYPE, STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.MAILBOX_ID)
                                .field(TYPE, STRING)
                                .field(INDEX, NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.PROPERTIES)
                                .field(TYPE, NESTED)
                                .startObject(PROPERTIES)
                                    .startObject(JsonMessageConstants.Property.NAMESPACE)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                    .startObject(JsonMessageConstants.Property.NAME)
                                        .field(TYPE, STRING)
                                        .field(INDEX, NOT_ANALYZED)
                                    .endObject()
                                    .startObject(JsonMessageConstants.Property.VALUE)
                                        .field(TYPE, STRING)
                                    .endObject()
                                .endObject()
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
