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

package org.apache.james.mailbox.elasticsearch.query;

import org.apache.james.mailbox.elasticsearch.json.JsonMessageConstants;
import org.apache.james.mailbox.model.SearchQuery;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

public class SortConverter {

    public static FieldSortBuilder convertSort(SearchQuery.Sort sort) {
        return SortBuilders.fieldSort(getFieldFromClause(sort.getSortClause()))
            .order(getOrder(sort));
    }

    private static String getFieldFromClause(SearchQuery.Sort.SortClause clause) {
        switch (clause) {
            case Arrival :
                return JsonMessageConstants.DATE;
            case MailboxCc :
                return JsonMessageConstants.CC;
            case MailboxFrom :
                return JsonMessageConstants.FROM + "." + JsonMessageConstants.EMailer.ADDRESS;
            case MailboxTo :
                return JsonMessageConstants.TO + "." + JsonMessageConstants.EMailer.ADDRESS;
            case BaseSubject :
                return JsonMessageConstants.SUBJECT;
            case Size :
                return JsonMessageConstants.SIZE;
            case SentDate :
                return JsonMessageConstants.DATE;
            case Uid :
                return JsonMessageConstants.ID;
            case DisplayFrom:
                return JsonMessageConstants.FROM + "." + JsonMessageConstants.EMailer.NAME;
            case DisplayTo:
                return JsonMessageConstants.TO + "." + JsonMessageConstants.EMailer.NAME;
            default:
                throw new RuntimeException("Sort is not implemented");
        }
    }

    private static SortOrder getOrder(SearchQuery.Sort sort) {
        if( sort.isReverse() ) {
            return SortOrder.DESC;
        } else {
            return SortOrder.ASC;
        }
    }
}
