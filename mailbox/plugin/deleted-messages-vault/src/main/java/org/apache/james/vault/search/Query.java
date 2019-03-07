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

package org.apache.james.vault.search;

import java.util.List;
import java.util.function.Predicate;

import org.apache.james.vault.DeletedMessage;

import com.google.common.collect.ImmutableList;

public class Query {
    public static final Query ALL = new Query(ImmutableList.of());
    private static final Predicate<DeletedMessage> MATCH_ALL = any -> true;

    public static Query and(List<Criterion> criteria) {
        return new Query(criteria);
    }

    public static Query of(Criterion... criteria) {
        return new Query(ImmutableList.copyOf(criteria));
    }

    private final List<Criterion> criteria;

    private Query(List<Criterion> criteria) {
        this.criteria = criteria;
    }

    public Predicate<DeletedMessage> toPredicate() {
        return criteria.stream()
            .map(Criterion::toPredicate)
            .reduce(Predicate::and)
            .orElse(MATCH_ALL);
    }
}
