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

import java.util.Iterator;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;

public class UsersRepositoryImpl<T extends UsersDAO> implements UsersRepository, Configurable {
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(UsersRepositoryImpl.class);
    private static String ILLEGAL_USERNAME_CHARACTERS = "\"(),:; <>@[\\]";

    private final DomainList domainList;
    protected final T usersDAO;
    private boolean virtualHosting;
    private Optional<Username> administratorId;

    @Inject
    public UsersRepositoryImpl(DomainList domainList, T usersDAO) {
        this.domainList = domainList;
        this.usersDAO = usersDAO;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        virtualHosting = configuration.getBoolean("enableVirtualHosting", usersDAO.getDefaultVirtualHostingValue());
        administratorId = Optional.ofNullable(configuration.getString("administratorId"))
            .map(Username::of);
    }

    public void setEnableVirtualHosting(boolean virtualHosting) {
        this.virtualHosting = virtualHosting;
    }

    @Override
    public void assertValid(Username username) throws UsersRepositoryException {
        assertDomainPartValid(username);
        assertLocalPartValid(username);
    }

    protected void assertDomainPartValid(Username username) throws UsersRepositoryException {
        if (supportVirtualHosting()) {
            // need a @ in the username
            if (!username.hasDomainPart()) {
                throw new InvalidUsernameException("Given Username needs to contain a @domainpart");
            } else {
                Domain domain = username.getDomainPart().get();
                try {
                    if (!domainList.containsDomain(domain)) {
                        throw new InvalidUsernameException("Domain does not exist in DomainList");
                    }
                } catch (DomainListException e) {
                    throw new UsersRepositoryException("Unable to query DomainList", e);
                }
            }
        } else {
            // @ only allowed when virtualhosting is supported
            if (username.hasDomainPart()) {
                throw new InvalidUsernameException("Given Username contains a @domainpart but virtualhosting support is disabled");
            }
        }
    }

    private void assertLocalPartValid(Username username) throws InvalidUsernameException {
        boolean isValid = CharMatcher.anyOf(ILLEGAL_USERNAME_CHARACTERS)
            .matchesNoneOf(username.getLocalPart());
        if (!isValid) {
            throw new InvalidUsernameException(String.format("Given Username '%s' should not contain any of those characters: %s",
                username.asString(), ILLEGAL_USERNAME_CHARACTERS));
        }
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        if (!contains(username)) {
            assertValid(username);
            usersDAO.addUser(username, password);
        } else {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exists!");
        }
    }

    @Override
    public User getUserByName(Username name) throws UsersRepositoryException {
        return usersDAO.getUserByName(name).orElse(null);
    }

    @Override
    public boolean test(Username name, String password) throws UsersRepositoryException {
        return usersDAO.getUserByName(name)
            .map(x -> x.verifyPassword(password))
            .orElseGet(() -> {
                LOGGER.info("Could not retrieve user {}. Password is unverified.", name);
                return false;
            });
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        assertDomainPartValid(user.getUserName());
        usersDAO.updateUser(user);
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        assertDomainPartValid(name);
        usersDAO.removeUser(name);
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return usersDAO.contains(name);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return usersDAO.countUsers();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return usersDAO.list();
    }

    @Override
    public boolean supportVirtualHosting() {
        return virtualHosting;
    }

    @VisibleForTesting void setAdministratorId(Optional<Username> username) {
        this.administratorId = username;
    }

    @Override
    public boolean isAdministrator(Username username) throws UsersRepositoryException {
        assertValid(username);

        return administratorId.map(id -> id.equals(username))
            .orElse(false);
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public MailAddress getMailAddressFor(Username username) throws UsersRepositoryException {
        try {
            if (supportVirtualHosting()) {
                return new MailAddress(username.asString());
            }
            return new MailAddress(username.getLocalPart(), domainList.getDefaultDomain());
        } catch (Exception e) {
            throw new UsersRepositoryException("Failed to compute mail address associated with the user", e);
        }
    }
}
