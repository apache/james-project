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

package org.apache.james.user.memory;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.lib.UsersRepositoryImpl;

public class MemoryUsersRepository extends UsersRepositoryImpl<MemoryUsersDAO> {

    public static MemoryUsersRepository withVirtualHosting(DomainList domainList) {
         MemoryUsersRepository userRepository = new MemoryUsersRepository(domainList);
         userRepository.setEnableVirtualHosting(true);
         return userRepository;
    }

    public static MemoryUsersRepository withoutVirtualHosting(DomainList domainList) {
        MemoryUsersRepository userRepository = new MemoryUsersRepository(domainList);
        userRepository.setEnableVirtualHosting(false);
        return userRepository;
    }

    private MemoryUsersRepository(DomainList domainList) {
        super(domainList, new MemoryUsersDAO());
    }

    public void clear() {
        usersDAO.clear();
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        super.configure(config);
        usersDAO.configure(config);
    }
}
