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
package org.apache.james.user.lib;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryManagementMBean;
import org.apache.james.user.api.model.JamesUser;
import org.apache.james.user.api.model.User;

@SuppressWarnings("deprecation")
public class UsersRepositoryManagement extends StandardMBean implements UsersRepositoryManagementMBean {

    /**
     * The administered UsersRepository
     */
    private UsersRepository usersRepository;

    @Inject
    public void setUsersRepository(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    public UsersRepositoryManagement() throws NotCompliantMBeanException {
        super(UsersRepositoryManagementMBean.class);
    }

    private JamesUser getJamesUser(String userName) throws UsersRepositoryException {
        User baseuser = usersRepository.getUserByName(userName);
        if (baseuser == null)
            throw new IllegalArgumentException("user not found: " + userName);
        if (!(baseuser instanceof JamesUser))
            throw new IllegalArgumentException("user is not of type JamesUser: " + userName);

        return (JamesUser) baseuser;
    }

    @Override
    public void addUser(String userName, String password) throws Exception {
        try {
            usersRepository.addUser(userName, password);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void deleteUser(String userName) throws Exception {
        try {
            usersRepository.removeUser(userName);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public boolean verifyExists(String userName) throws Exception {
        try {
            return usersRepository.contains(userName);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public long countUsers() throws Exception {
        try {
            return usersRepository.countUsers();
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public String[] listAllUsers() throws Exception {
        List<String> userNames = new ArrayList<>();
        try {
            for (Iterator<String> it = usersRepository.list(); it.hasNext(); ) {
                userNames.add(it.next());
            }
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());

        }
        return userNames.toArray(new String[userNames.size()]);
    }

    @Override
    public void setPassword(String userName, String password) throws Exception {
        try {
            User user = usersRepository.getUserByName(userName);
            if (user == null)
                throw new UsersRepositoryException("user not found: " + userName);
            if (!user.setPassword(password)) {
                throw new UsersRepositoryException("Unable to update password for user " + user);
            }
            usersRepository.updateUser(user);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());

        }

    }

    @Override
    public void unsetAlias(String userName) throws Exception {
        try {
            JamesUser user = getJamesUser(userName);
            if (!user.getAliasing()) {
                throw new UsersRepositoryException("User " + user + " is no alias");
            }
            user.setAliasing(false);
            usersRepository.updateUser(user);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public String getAlias(String userName) throws Exception {
        try {
            JamesUser user = getJamesUser(userName);
            if (!user.getAliasing()) {
                return null;
            }
            return user.getAlias();
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());

        }
    }

    @Override
    public void unsetForwardAddress(String userName) throws Exception {
        try {
            JamesUser user = getJamesUser(userName);
            if (!user.getForwarding()) {
                throw new UsersRepositoryException("User " + user + " is no forward");
            }
            user.setForwarding(false);
            usersRepository.updateUser(user);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public String getForwardAddress(String userName) throws Exception {
        try {
            JamesUser user = getJamesUser(userName);
            if (!user.getForwarding()) {
                return null;
            }
            return user.getForwardingDestination().toString();
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public boolean getVirtualHostingEnabled() throws Exception {
        try {
            return usersRepository.supportVirtualHosting();
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }
}
