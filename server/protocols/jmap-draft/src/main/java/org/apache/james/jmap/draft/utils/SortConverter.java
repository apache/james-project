/****************************************************************
 O * Licensed to the Apache Software Foundation (ASF) under one   *
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

package org.apache.james.jmap.draft.utils;

import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.Sort;
import org.apache.james.mailbox.model.SearchQuery.Sort.Order;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public class SortConverter {

    private static final String SEPARATOR = " ";
    private static final String DESC_ORDERING = "desc";
    private static final String ASC_ORDERING = "asc";

    private static final Map<String, SortClause> SORT_CLAUSE_MAP =
        ImmutableMap.<String, SortClause>builder()
            .put("date", SortClause.SentDate)
            .put("id", SortClause.Id)
            .put("subject", SortClause.BaseSubject)
            .put("from", SortClause.MailboxFrom)
            .put("to", SortClause.MailboxTo)
            .put("size", SortClause.Size)
            .build();


    public static List<Sort> convertToSorts(List<String> jmapSorts) {
        Preconditions.checkNotNull(jmapSorts);
        return jmapSorts.stream()
            .map(SortConverter::toSort)
            .collect(Guavate.toImmutableList());
    }

    private static Sort toSort(String jmapSort) {
        Preconditions.checkNotNull(jmapSort);
        List<String> splitToList = Splitter.on(SEPARATOR).splitToList(jmapSort);
        checkField(splitToList);
        return new SearchQuery.Sort(getSortClause(splitToList.get(0)),
            isReverse(splitToList));
    }

    private static SortClause getSortClause(String field) {
        if (! SORT_CLAUSE_MAP.containsKey(field)) {
            throw new IllegalArgumentException("Unknown sorting field: " + field + " should be one of " + SORT_CLAUSE_MAP.keySet());
        }
        return SORT_CLAUSE_MAP.get(field);
    }

    private static Order isReverse(List<String> splitList) {
        if (splitList.size() == 1) {
            return Order.REVERSE;
        }
        String order = splitList.get(1);
        switch (order) {
            case DESC_ORDERING:
                return Order.REVERSE;
            case ASC_ORDERING:
                return Order.NATURAL;
        }
        throw new IllegalArgumentException("Unknown sorting order: " + order + " should be one of [asc, desc]");
    }

    private static void checkField(List<String> splitToList) {
        Preconditions.checkArgument(splitToList.size() > 0 && splitToList.size() <= 2, "Bad sort field definition. Must contains a field and an optional order separated by a space");
    }

}
