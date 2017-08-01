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
import java.util.Locale;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractUsersRepository;
import org.apache.james.user.lib.model.DefaultUser;

import com.google.common.base.Optional;

public class MemoryUsersRepository extends AbstractUsersRepository {

    public static MemoryUsersRepository withVirtualHosting() {
        return new MemoryUsersRepository(true);
    }

    public static MemoryUsersRepository withoutVirtualHosting() {
        return new MemoryUsersRepository(false);
    }
    
    private final Map<String, User> userByName;
    private final boolean supportVirtualHosting;
    private String algo;

    private MemoryUsersRepository(boolean supportVirtualHosting) {
        this.userByName = new HashMap<String, User>();
        this.algo = "MD5";
        this.supportVirtualHosting = supportVirtualHosting;
    }

    public void clear() {
        userByName.clear();
    }

    @Override
    public boolean supportVirtualHosting() {
        return supportVirtualHosting;
    }

    @Override
    public void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
        algo = config.getString("algorithm", "MD5");
        super.doConfigure(config);
    }

    @Override
    protected void doAddUser(String username, String password) throws UsersRepositoryException {
        DefaultUser user = new DefaultUser(username, algo);
        user.setPassword(password);
        userByName.put(username.toLowerCase(Locale.US), user);
    }

    @Override
    public User getUserByName(String name) throws UsersRepositoryException {
        return userByName.get(name);
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        User existingUser = getUserByName(user.getUserName());
        if (existingUser == null) {
            throw new UsersRepositoryException("Please provide an existing user to update");
        }
        userByName.put(user.getUserName().toLowerCase(Locale.US), user);
    }

    @Override
    public void removeUser(String name) throws UsersRepositoryException {
        if (userByName.remove(name) == null) {
            throw new UsersRepositoryException("unable to remove unknown user " + name);
        }
    }

    @Override
    public boolean contains(String name) throws UsersRepositoryException {
        return userByName.containsKey(name.toLowerCase(Locale.US));
    }

    @Override
    public boolean test(String name, final String password) throws UsersRepositoryException {
        return Optional.fromNullable(userByName.get(name))
            .transform(user -> user.verifyPassword(password))
            .or(false);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return userByName.size();
    }

    @Override
    public Iterator<String> list() throws UsersRepositoryException {
        return userByName.keySet().iterator();
    }
}
