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
import org.apache.james.lifecycle.api.LogEnabled;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.slf4j.Logger;

public abstract class AbstractUsersRepository implements UsersRepository, LogEnabled, Configurable {

    private DomainList domainList;
    private boolean virtualHosting;
    private Logger logger;

    protected Logger getLogger() {
        return logger;
    }

    /**
     * @see org.apache.james.lifecycle.api.LogEnabled#setLog(org.slf4j.Logger)
     */
    public void setLog(Logger logger) {
        this.logger = logger;
    }

    /**
     * @see
     * org.apache.james.lifecycle.api.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration configuration) throws ConfigurationException {

        virtualHosting = configuration.getBoolean("enableVirtualHosting", getDefaultVirtualHostingValue());

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
}
