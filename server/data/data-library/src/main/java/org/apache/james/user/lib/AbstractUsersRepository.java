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

import javax.inject.Inject;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.MailAddress;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;

public abstract class AbstractUsersRepository implements UsersRepository, Configurable {

    private DomainList domainList;
    private boolean virtualHosting;
    private Optional<String> administratorId;

    /**
     * @see
     * org.apache.james.lifecycle.api.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {

        virtualHosting = configuration.getBoolean("enableVirtualHosting", getDefaultVirtualHostingValue());
        administratorId = Optional.fromNullable(configuration.getString("administratorId"));

        doConfigure(configuration);
    }

    protected boolean getDefaultVirtualHostingValue() {
        return false;
    }

    protected void doConfigure(HierarchicalConfiguration config) throws ConfigurationException {
    }

    public void setEnableVirtualHosting(boolean virtualHosting) {
        this.virtualHosting = virtualHosting;
    }

    @Inject
    public void setDomainList(DomainList domainList) {
        this.domainList = domainList;
    }

    protected void isValidUsername(String username) throws UsersRepositoryException {
        int i = username.indexOf("@");
        if (supportVirtualHosting()) {
            // need a @ in the username
            if (i == -1) {
                throw new UsersRepositoryException("Given Username needs to contain a @domainpart");
            } else {
                String domain = username.substring(i + 1);
                try {
                    if (!domainList.containsDomain(domain)) {
                        throw new UsersRepositoryException("Domain does not exist in DomainList");
                    } else {
                    }
                } catch (DomainListException e) {
                    throw new UsersRepositoryException("Unable to query DomainList", e);
                }
            }
        } else {
            // @ only allowed when virtualhosting is supported
            if (i != -1) {
                throw new UsersRepositoryException("Given Username contains a @domainpart but virtualhosting support is disabled");
            }
        }
    }

    /**
     * @see org.apache.james.user.api.UsersRepository#addUser(java.lang.String,
     * java.lang.String)
     */
    public void addUser(String username, String password) throws UsersRepositoryException {

        if (!contains(username)) {
            isValidUsername(username);
            doAddUser(username, password);
        } else {
            throw new AlreadyExistInUsersRepositoryException("User with username " + username + " already exists!");
        }

    }

    /**
     * @see org.apache.james.user.api.UsersRepository#supportVirtualHosting()
     */
    public boolean supportVirtualHosting() {
        return virtualHosting;
    }

    /**
     * Add the user with the given username and password
     * 
     * @param username
     * @param password
     * @throws UsersRepositoryException
     *           If an error occurred
     */
    protected abstract void doAddUser(String username, String password) throws UsersRepositoryException;

    @Override
    public String getUser(MailAddress mailAddress) throws UsersRepositoryException {
        if (supportVirtualHosting()) {
            return mailAddress.asString();
        } else {
            return mailAddress.getLocalPart();
        }
    }

    @VisibleForTesting void setAdministratorId(Optional<String> username) {
        this.administratorId = username;
    }

    @Override
    public boolean isAdministrator(String username) throws UsersRepositoryException {
        if (administratorId.isPresent()) {
            return administratorId.get().equals(username);
        }
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }
}
