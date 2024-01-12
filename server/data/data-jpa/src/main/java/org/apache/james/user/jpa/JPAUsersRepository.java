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

package org.apache.james.user.jpa;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.lib.UsersRepositoryImpl;

/**
 * JPA based UserRepository
 */
public class JPAUsersRepository extends UsersRepositoryImpl<JPAUsersDAO> {
    @Inject
    public JPAUsersRepository(DomainList domainList) {
        super(domainList, new JPAUsersDAO());
    }

    /**
     * Sets entity manager.
     * 
     * @param entityManagerFactory
     *            the entityManager to set
     */
    @Inject
    @PersistenceUnit(unitName = "James")
    public final void setEntityManagerFactory(EntityManagerFactory entityManagerFactory) {
        usersDAO.setEntityManagerFactory(entityManagerFactory);
    }

    @PostConstruct
    public void init() {
        usersDAO.init();
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        usersDAO.configure(config);
        super.configure(config);
    }
}
