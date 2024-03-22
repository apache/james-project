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

package org.apache.james.mailrepository.jpa;

import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;

import org.apache.james.backends.jpa.TransactionRunner;
import org.apache.james.mailrepository.api.MailRepositoryUrl;
import org.apache.james.mailrepository.api.MailRepositoryUrlStore;
import org.apache.james.mailrepository.jpa.model.JPAUrl;

public class JPAMailRepositoryUrlStore implements MailRepositoryUrlStore {
    private final TransactionRunner transactionRunner;

    @Inject
    public JPAMailRepositoryUrlStore(EntityManagerFactory entityManagerFactory) {
        this.transactionRunner = new TransactionRunner(entityManagerFactory);
    }

    @Override
    public void add(MailRepositoryUrl url) {
        transactionRunner.run(entityManager ->
            entityManager.merge(JPAUrl.from(url)));
    }

    @Override
    public Stream<MailRepositoryUrl> listDistinct() {
        return transactionRunner.runAndRetrieveResult(entityManager ->
            entityManager
                .createNamedQuery("listUrls", JPAUrl.class)
                .getResultList()
                .stream()
                .map(JPAUrl::toMailRepositoryUrl));
    }

    @Override
    public boolean contains(MailRepositoryUrl url) {
        return transactionRunner.runAndRetrieveResult(entityManager ->
            ! entityManager.createNamedQuery("getUrl", JPAUrl.class)
                .setParameter("value", url.asString())
                .getResultList()
                .isEmpty());
    }
}

