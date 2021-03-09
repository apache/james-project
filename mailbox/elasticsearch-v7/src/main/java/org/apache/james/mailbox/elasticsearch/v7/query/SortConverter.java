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

package org.apache.james.mailbox.elasticsearch.v7.query;

import org.apache.james.backends.es.v7.IndexCreationFactory;
import org.apache.james.mailbox.elasticsearch.v7.json.JsonMessageConstants;
import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortMode;
import org.elasticsearch.search.sort.SortOrder;

public class SortConverter {

    private static final String PATH_SEPARATOR = ".";

    public static FieldSortBuilder convertSort(SearchQuery.Sort sort) {
        return getSortClause(sort.getSortClause())
            .order(getOrder(sort))
            .sortMode(SortMode.MIN);
    }

    private static FieldSortBuilder getSortClause(SearchQuery.Sort.SortClause clause) {
        switch (clause) {
            case Arrival :
                return SortBuilders.fieldSort(JsonMessageConstants.DATE);
            case MailboxCc :
                return SortBuilders.fieldSort(JsonMessageConstants.CC + PATH_SEPARATOR + JsonMessageConstants.EMailer.ADDRESS
                    + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case MailboxFrom :
                return SortBuilders.fieldSort(JsonMessageConstants.FROM + PATH_SEPARATOR + JsonMessageConstants.EMailer.ADDRESS
                    + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case MailboxTo :
                return SortBuilders.fieldSort(JsonMessageConstants.TO + PATH_SEPARATOR + JsonMessageConstants.EMailer.ADDRESS
                    + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case BaseSubject :
                return SortBuilders.fieldSort(JsonMessageConstants.SUBJECT + PATH_SEPARATOR + IndexCreationFactory.RAW);
            case Size :
                return SortBuilders.fieldSort(JsonMessageConstants.SIZE);
            case SentDate :
                return SortBuilders.fieldSort(JsonMessageConstants.SENT_DATE);
            case Uid :
                return SortBuilders.fieldSort(JsonMessageConstants.UID);
            case Id:
                return SortBuilders.fieldSort(JsonMessageConstants.MESSAGE_ID);
            default:
                throw new RuntimeException("Sort is not implemented");
        }
    }

    private static SortOrder getOrder(SearchQuery.Sort sort) {
        if (sort.isReverse()) {
            return SortOrder.DESC;
        } else {
            return SortOrder.ASC;
        }
    }
}
