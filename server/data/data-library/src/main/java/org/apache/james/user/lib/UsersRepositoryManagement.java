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

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryManagementMBean;
import org.apache.james.user.api.model.User;

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

    @Override
    public void addUser(String rawUsername, String password) throws Exception {
        try {
            Username userName = Username.of(rawUsername);
            usersRepository.addUser(userName, password);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public void deleteUser(String rawUsername) throws Exception {
        try {
            Username userName = Username.of(rawUsername);
            usersRepository.removeUser(userName);
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());
        }
    }

    @Override
    public boolean verifyExists(String rawUsername) throws Exception {
        try {
            Username userName = Username.of(rawUsername);
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
            for (Iterator<Username> it = usersRepository.list(); it.hasNext(); ) {
                userNames.add(it.next().asString());
            }
        } catch (UsersRepositoryException e) {
            throw new Exception(e.getMessage());

        }
        return userNames.toArray(String[]::new);
    }

    @Override
    public void setPassword(String rawUsername, String password) throws Exception {
        try {
            Username userName = Username.of(rawUsername);
            User user = usersRepository.getUserByName(userName);
            if (user == null) {
                throw new UsersRepositoryException("user not found: " + userName.asString());
            }
            if (!user.setPassword(password)) {
                throw new UsersRepositoryException("Unable to update password for user " + user);
            }
            usersRepository.updateUser(user);
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
