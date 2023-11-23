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

package org.apache.james.mailbox.opensearch.query;

import static org.apache.james.backends.opensearch.IndexCreationFactory.RAW;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.CC;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.EMailer.ADDRESS;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.EMailer.NAME;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.FROM;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.MESSAGE_ID;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.SENT_DATE;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.SIZE;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.SUBJECT;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.TO;
import static org.apache.james.mailbox.opensearch.json.JsonMessageConstants.UID;

import java.util.stream.Stream;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortMode;
import org.opensearch.client.opensearch._types.SortOrder;

public class SortConverter {

    private static final String PATH_SEPARATOR = ".";

    public static Stream<FieldSort> convertSort(SearchQuery.Sort sort) {
        return getSortClause(sort.getSortClause())
            .map(clause -> clause.order(getOrder(sort))
                .mode(SortMode.Min)
                .build());
    }

    private static Stream<FieldSort.Builder> getSortClause(SearchQuery.Sort.SortClause clause) {
        switch (clause) {
            case Arrival :
                return Stream.of(new FieldSort.Builder().field(JsonMessageConstants.DATE));
            case MailboxCc :
                return Stream.of(new FieldSort.Builder().field(CC + PATH_SEPARATOR + NAME + PATH_SEPARATOR + RAW),
                    new FieldSort.Builder().field(CC + PATH_SEPARATOR + ADDRESS + PATH_SEPARATOR + RAW));
            case MailboxFrom :
                return Stream.of(new FieldSort.Builder().field(FROM + PATH_SEPARATOR + NAME + PATH_SEPARATOR + RAW),
                    new FieldSort.Builder().field(CC + PATH_SEPARATOR + ADDRESS + PATH_SEPARATOR + RAW));
            case MailboxTo :
                return Stream.of(new FieldSort.Builder().field(TO + PATH_SEPARATOR + NAME + PATH_SEPARATOR + RAW),
                    new FieldSort.Builder().field(CC + PATH_SEPARATOR + ADDRESS + PATH_SEPARATOR + RAW));
            case BaseSubject :
                return Stream.of(new FieldSort.Builder().field(SUBJECT + PATH_SEPARATOR + RAW));
            case Size :
                return Stream.of(new FieldSort.Builder().field(SIZE));
            case SentDate :
                return Stream.of(new FieldSort.Builder().field(SENT_DATE));
            case Uid :
                return Stream.of(new FieldSort.Builder().field(UID));
            case Id:
                return Stream.of(new FieldSort.Builder().field(MESSAGE_ID));
            default:
                throw new RuntimeException("Sort is not implemented");
        }
    }

    private static SortOrder getOrder(SearchQuery.Sort sort) {
        if (sort.isReverse()) {
            return SortOrder.Desc;
        } else {
            return SortOrder.Asc;
        }
    }
}
