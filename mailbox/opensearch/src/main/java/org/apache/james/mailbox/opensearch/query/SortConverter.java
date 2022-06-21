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

import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.opensearch.json.JsonMessageConstants;
import org.opensearch.client.opensearch._types.FieldSort;
import org.opensearch.client.opensearch._types.SortMode;
import org.opensearch.client.opensearch._types.SortOrder;

public class SortConverter {

    private static final String PATH_SEPARATOR = ".";

    public static FieldSort convertSort(SearchQuery.Sort sort) {
        return getSortClause(sort.getSortClause())
            .order(getOrder(sort))
            .mode(SortMode.Min)
            .build();
    }

    private static FieldSort.Builder getSortClause(SearchQuery.Sort.SortClause clause) {
        switch (clause) {
            case Arrival :
                return new FieldSort.Builder().field(JsonMessageConstants.DATE);
            case MailboxCc :
                return new FieldSort.Builder().field(JsonMessageConstants.CC + PATH_SEPARATOR
                    + JsonMessageConstants.EMailer.ADDRESS + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case MailboxFrom :
                return new FieldSort.Builder().field(JsonMessageConstants.FROM + PATH_SEPARATOR
                    + JsonMessageConstants.EMailer.ADDRESS + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case MailboxTo :
                return new FieldSort.Builder().field(JsonMessageConstants.TO + PATH_SEPARATOR
                    + JsonMessageConstants.EMailer.ADDRESS + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case BaseSubject :
                return new FieldSort.Builder().field(JsonMessageConstants.SUBJECT + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case Size :
                return new FieldSort.Builder().field(JsonMessageConstants.SIZE);
            case SentDate :
                return new FieldSort.Builder().field(JsonMessageConstants.SENT_DATE);
            case Uid :
                return new FieldSort.Builder().field(JsonMessageConstants.UID);
            case Id:
                return new FieldSort.Builder().field(JsonMessageConstants.MESSAGE_ID);
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
