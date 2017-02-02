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

import org.apache.james.backends.es.ElasticSearchIndexer;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.NodeMappingFactory;
import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;

import com.google.common.base.Throwables;

public class MailboxMappingFactory {

    public static XContentBuilder getMappingContent() {
        try {
            return jsonBuilder()
                .startObject()

                    .startObject(MailboxElasticsearchConstants.MESSAGE_TYPE.getValue())
                        .startObject(NodeMappingFactory.PROPERTIES)
                            .startObject(JsonMessageConstants.MESSAGE_ID)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.UID)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.LONG)
                            .endObject()
                            .startObject(JsonMessageConstants.MODSEQ)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.LONG)
                            .endObject()
                            .startObject(JsonMessageConstants.SIZE)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.LONG)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_ANSWERED)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_DELETED)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_DRAFT)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_FLAGGED)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_RECENT)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.IS_UNREAD)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.BOOLEAN)
                            .endObject()
                            .startObject(JsonMessageConstants.DATE)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.DATE)
                                .field(NodeMappingFactory.FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                            .endObject()
                            .startObject(JsonMessageConstants.SENT_DATE)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.DATE)
                                .field(NodeMappingFactory.FORMAT, "yyyy-MM-dd'T'HH:mm:ssZ")
                            .endObject()
                            .startObject(JsonMessageConstants.MEDIA_TYPE)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.SUBTYPE)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.USER_FLAGS)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()

                            .startObject(JsonMessageConstants.FROM)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.NESTED)
                                .startObject(NodeMappingFactory.PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .startObject(NodeMappingFactory.FIELDS)
                                            .startObject(NodeMappingFactory.RAW)
                                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                                .field(NodeMappingFactory.ANALYZER, IndexCreationFactory.CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.SUBJECT)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .startObject(NodeMappingFactory.FIELDS)
                                    .startObject(NodeMappingFactory.RAW)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.ANALYZER, IndexCreationFactory.CASE_INSENSITIVE)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.TO)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.NESTED)
                                .startObject(NodeMappingFactory.PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .startObject(NodeMappingFactory.FIELDS)
                                            .startObject(NodeMappingFactory.RAW)
                                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                                .field(NodeMappingFactory.ANALYZER, IndexCreationFactory.CASE_INSENSITIVE)
                                            .endObject()
                                        .endObject()
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.CC)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.NESTED)
                                .startObject(NodeMappingFactory.PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.BCC)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.NESTED)
                                .startObject(NodeMappingFactory.PROPERTIES)
                                    .startObject(JsonMessageConstants.EMailer.NAME)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                    .endObject()
                                    .startObject(JsonMessageConstants.EMailer.ADDRESS)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.MAILBOX_ID)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.USERS)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                            .endObject()
                            .startObject(JsonMessageConstants.PROPERTIES)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.NESTED)
                                .startObject(NodeMappingFactory.PROPERTIES)
                                    .startObject(JsonMessageConstants.Property.NAMESPACE)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                    .startObject(JsonMessageConstants.Property.NAME)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.INDEX, NodeMappingFactory.NOT_ANALYZED)
                                    .endObject()
                                    .startObject(JsonMessageConstants.Property.VALUE)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.TEXT_BODY)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .startObject(NodeMappingFactory.FIELDS)
                                    .startObject(NodeMappingFactory.RAW)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.ANALYZER, IndexCreationFactory.CASE_INSENSITIVE)
                                        .field(NodeMappingFactory.IGNORE_ABOVE, NodeMappingFactory.LUCENE_LIMIT)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.HTML_BODY)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .startObject(NodeMappingFactory.FIELDS)
                                    .startObject(NodeMappingFactory.RAW)
                                        .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                        .field(NodeMappingFactory.ANALYZER, IndexCreationFactory.CASE_INSENSITIVE)
                                        .field(NodeMappingFactory.IGNORE_ABOVE, NodeMappingFactory.LUCENE_LIMIT)
                                    .endObject()
                                .endObject()
                            .endObject()

                            .startObject(JsonMessageConstants.TEXT)
                                .field(NodeMappingFactory.TYPE, NodeMappingFactory.STRING)
                                .field(NodeMappingFactory.ANALYZER, NodeMappingFactory.SNOWBALL)
                                .field(NodeMappingFactory.IGNORE_ABOVE, NodeMappingFactory.LUCENE_LIMIT)
                            .endObject()
                        .endObject()
                    .endObject()
                .endObject();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
