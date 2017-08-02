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
package org.apache.james.user.lib.mock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.apache.james.user.lib.AbstractJamesUsersRepository;
import org.apache.james.user.lib.model.DefaultJamesUser;
import org.apache.james.user.lib.model.DefaultUser;

@SuppressWarnings("deprecation")
public class InMemoryUsersRepository extends AbstractJamesUsersRepository {

    private final HashMap<String, User> m_users = new HashMap<>();
    /**
     * force the repository to hold implementations of JamesUser interface,
     * instead of User JamesUser is _not_ required as of the UsersRepository
     * interface, so the necessarity forcing it is due to code using
     * UsersRepository while at the same time expecting it to hold JamesUsers
     * (like in RemoteManagerHandler)
     */
    private boolean m_forceUseJamesUser = false;

    public void setForceUseJamesUser() {
        m_forceUseJamesUser = true;
    }

    @Override
    public User getUserByName(String name) throws UsersRepositoryException {
        if (ignoreCase) {
            return getUserByNameCaseInsensitive(name);
        } else {
            return m_users.get(name);
        }
    }

    public User getUserByNameCaseInsensitive(String name) {
        return m_users.get(name.toLowerCase(Locale.US));
    }

    public String getRealName(String name) {
        if (ignoreCase) {
            return m_users.get(name.toLowerCase(Locale.US)) != null ? m_users.get(name.toLowerCase(Locale.US)).
                    getUserName() : null;
        } else {
            return m_users.get(name) != null ? name : null;
        }
    }

    @Override
    public void removeUser(String name) throws UsersRepositoryException {
        if (!m_users.containsKey(name)) {
            throw new UsersRepositoryException("No such user");
        } else {
            m_users.remove(name);
        }
    }

    @Override
    public boolean contains(String name) throws UsersRepositoryException {
        if (ignoreCase) {
            return containsCaseInsensitive(name);
        } else {
            return m_users.containsKey(name);
        }
    }

    public boolean containsCaseInsensitive(String name) {
        throw new UnsupportedOperationException("mock");
    }

    @Override
    public boolean test(String name, String password) throws UsersRepositoryException {
        User user = getUserByName(name);
        return user != null && user.verifyPassword(password);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return m_users.size();
    }

    protected List<String> listUserNames() {
        Iterator<User> users = m_users.values().iterator();
        List<String> userNames = new LinkedList<>();
        while (users.hasNext()) {
            User user = users.next();
            userNames.add(user.getUserName());
        }

        return userNames;
    }

    @Override
    public Iterator<String> list() throws UsersRepositoryException {
        return listUserNames().iterator();
    }

    @Override
    protected void doAddUser(User user) throws UsersRepositoryException {
        if (m_forceUseJamesUser && user instanceof DefaultUser) {
            DefaultUser aUser = (DefaultUser) user;
            user = new DefaultJamesUser(aUser.getUserName(), aUser.getHashedPassword(), aUser.getHashAlgorithm());
        }

        String key = user.getUserName();
        m_users.put(key, user);
    }

    @Override
    protected void doUpdateUser(User user) throws UsersRepositoryException {
        if (m_users.containsKey(user.getUserName())) {
            m_users.put(user.getUserName(), user);
        } else {
            throw new UsersRepositoryException("No such user");
        }
    }
}
