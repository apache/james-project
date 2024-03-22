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

package org.apache.james.quota.search.scanning;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.quota.search.Limit;
import org.apache.james.quota.search.QuotaQuery;
import org.apache.james.quota.search.QuotaSearcher;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.util.streams.Iterators;

import com.google.common.collect.ImmutableList;

public class ScanningQuotaSearcher implements QuotaSearcher {
    private final UsersRepository usersRepository;
    private final ClauseConverter clauseConverter;

    @Inject
    public ScanningQuotaSearcher(UsersRepository usersRepository, ClauseConverter clauseConverter) {
        this.usersRepository = usersRepository;
        this.clauseConverter = clauseConverter;
    }

    @Override
    public List<Username> search(QuotaQuery query) {
        Stream<Username> results = Iterators.toStream(listUsers())
            .filter(clauseConverter.andToPredicate(query.getClause()))
            .sorted(Comparator.comparing(Username::asString))
            .skip(query.getOffset().getValue());

        return limit(results, query.getLimit())
            .collect(ImmutableList.toImmutableList());
    }

    private Stream<Username> limit(Stream<Username> results, Limit limit) {
        return limit.getValue()
            .map(results::limit)
            .orElse(results);
    }

    private Iterator<Username> listUsers() {
        try {
            return usersRepository.list();
        } catch (UsersRepositoryException e) {
            throw new RuntimeException(e);
        }
    }
}
