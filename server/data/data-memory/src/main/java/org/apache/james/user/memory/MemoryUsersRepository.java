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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;

public class MemoryUsersRepository extends AbstractUsersRepository {

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
    
    private final Map<String, User> userByName;
    private String algo;

    private MemoryUsersRepository(DomainList domainList) {
        super(domainList);
        this.userByName = new HashMap<>();
        this.algo = "MD5";
    }

    public void clear() {
        userByName.clear();
    }

    @Override
    public void doConfigure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        algo = config.getString("algorithm", "MD5");
        super.doConfigure(config);
    }

    @Override
    protected void doAddUser(Username username, String password) {
        DefaultUser user = new DefaultUser(username, algo);
        user.setPassword(password);
        userByName.put(username.asString(), user);
    }

    @Override
    public User getUserByName(Username name) throws UsersRepositoryException {
        return userByName.get(name.asString());
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        assertDomainPartValid(user.getUserName());

        User existingUser = getUserByName(user.getUserName());
        if (existingUser == null) {
            throw new UsersRepositoryException("Please provide an existing user to update");
        }
        userByName.put(user.getUserName().asString(), user);
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        assertDomainPartValid(name);

        if (userByName.remove(name.asString()) == null) {
            throw new UsersRepositoryException("unable to remove unknown user " + name.asString());
        }
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return userByName.containsKey(name.asString());
    }

    @Override
    public boolean test(Username name, final String password) throws UsersRepositoryException {
        return Optional.ofNullable(userByName.get(name.asString()))
            .map(user -> user.verifyPassword(password))
            .orElse(false);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return userByName.size();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return userByName.keySet()
            .stream()
            .map(Username::of)
            .iterator();
    }
}
