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

package org.apache.james.user.ldap;

import java.util.Iterator;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.api.model.User;
import org.reactivestreams.Publisher;

/**
 * Spring support: do not attempt to mutualize the LDAP connection pool.
 */
public class LegacyReadOnlyUsersLDAPRepository implements Configurable, UsersRepository {
    private final DomainList domainList;
    private final GaugeRegistry gaugeRegistry;
    private ReadOnlyUsersLDAPRepository delegate;

    @Inject
    public LegacyReadOnlyUsersLDAPRepository(DomainList domainList,
                                             GaugeRegistry gaugeRegistry) {
        this.domainList = domainList;
        this.gaugeRegistry = gaugeRegistry;
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) throws ConfigurationException {
        try {
            delegate = new ReadOnlyUsersLDAPRepository(domainList, gaugeRegistry, LdapRepositoryConfiguration.from(config));
        } catch (Exception e) {
            throw new ConfigurationException(e);
        }
        delegate.configure(config);
    }

    @Override
    public void addUser(Username username, String password) throws UsersRepositoryException {
        delegate.addUser(username, password);
    }

    @Override
    public User getUserByName(Username name) throws UsersRepositoryException {
        return delegate.getUserByName(name);
    }

    @Override
    public void updateUser(User user) throws UsersRepositoryException {
        delegate.updateUser(user);
    }

    @Override
    public void removeUser(Username name) throws UsersRepositoryException {
        delegate.removeUser(name);
    }

    @Override
    public boolean contains(Username name) throws UsersRepositoryException {
        return delegate.contains(name);
    }

    @Override
    public Publisher<Boolean> containsReactive(Username name) {
        return delegate.containsReactive(name);
    }

    @Override
    public Optional<Username> test(Username name, String password) throws UsersRepositoryException {
        return delegate.test(name, password);
    }

    @Override
    public int countUsers() throws UsersRepositoryException {
        return delegate.countUsers();
    }

    @Override
    public Iterator<Username> list() throws UsersRepositoryException {
        return delegate.list();
    }

    @Override
    public Publisher<Username> listReactive() {
        return delegate.listReactive();
    }

    @Override
    public boolean supportVirtualHosting() {
        return delegate.supportVirtualHosting();
    }

    @Override
    public MailAddress getMailAddressFor(Username username) throws UsersRepositoryException {
        return delegate.getMailAddressFor(username);
    }

    @Override
    public boolean isAdministrator(Username username) throws UsersRepositoryException {
        return delegate.isAdministrator(username);
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }
}
