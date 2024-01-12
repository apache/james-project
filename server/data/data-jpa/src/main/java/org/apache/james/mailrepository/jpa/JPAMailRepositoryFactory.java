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

import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;

import org.apache.james.mailrepository.api.MailRepository;
import org.apache.james.mailrepository.api.MailRepositoryFactory;
import org.apache.james.mailrepository.api.MailRepositoryUrl;

import com.github.fge.lambdas.Throwing;

public class JPAMailRepositoryFactory implements MailRepositoryFactory {
    private final EntityManagerFactory entityManagerFactory;

    @Inject
    public JPAMailRepositoryFactory(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    @Override
    public Class<? extends MailRepository> mailRepositoryClass() {
        return JPAMailRepository.class;
    }

    @Override
    public MailRepository create(MailRepositoryUrl url) {
        // Injecting the url here is redundant since the class is also a
        // Configurable and the mail repository store will call #configure()
        // with the same effect. However, this paves the way to drop the
        // Configurable aspect in the future.
        return Throwing.supplier(() -> new JPAMailRepository(entityManagerFactory, url)).sneakyThrow().get();
    }
}
