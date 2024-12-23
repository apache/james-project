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

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.user.api.InvalidUsernameException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryImpl;

import com.github.fge.lambdas.Throwing;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;

import reactor.core.publisher.Mono;

/**
 * <p>
 * This repository implementation serves as a bridge between Apache James and
 * LDAP. It allows James to authenticate users against an LDAP compliant server
 * such as Apache DS or Microsoft AD. It also enables role/group based access
 * restriction based on LDAP groups (role/group based access are experimental
 * and untested, contributions welcomed).
 * </p>
 * <p>
 * It is intended for organisations that already have a user-authentication and
 * authorisation mechanism in place, and want to leverage this when deploying
 * James. The assumption inherent here is that such organisations would not want
 * to manage user details via James, but will do so externally using whatever
 * mechanism provided by, or built on top off, their LDAP implementation.
 * </p>
 * <p>
 * Based on this assumption, this repository is strictly <b>read-only</b>. As a
 * consequence, user modification, deletion and creation requests will be
 * ignored when using this repository.
 * </p>
 * <p>
 * The following fragment of XML provides an example configuration to enable
 * this repository: </br>
 *
 * <pre>
 *  &lt;users-store&gt;
 *      &lt;repository name=&quot;LDAPUsers&quot;
 *      class=&quot;org.apache.james.userrepository.ReadOnlyUsersLDAPRepository&quot;
 *      ldapHost=&quot;ldap://myldapserver:389&quot;
 *      principal=&quot;uid=ldapUser,ou=system&quot;
 *      credentials=&quot;password&quot;
 *      userBase=&quot;ou=People,o=myorg.com,ou=system&quot;
 *      userIdAttribute=&quot;uid&quot;
 *      userObjectClass=&quot;inetOrgPerson&quot;
 *      administratorId=&quot;ldapAdmin&quot;
 *  &lt;/users-store&gt;
 * </pre>
 *
 * </br>
 *
 * Its constituent attributes are defined as follows:
 * <ul>
 * <li><b>ldapHost:</b> The URL of the LDAP server to connect to.</li>
 * <li>
 * <b>principal:</b> (optional) The name (DN) of the user with which to
 * initially bind to the LDAP server.</li>
 * <li>
 * <b>credentials:</b> (optional) The password with which to initially bind to
 * the LDAP server.</li>
 * <li>
 * <b>userBase:</b>The context within which to search for user entities.</li>
 * <li>
 * <b>userIdAttribute:</b>The name of the LDAP attribute which holds user ids.
 * For example &quot;uid&quot; for Apache DS, or &quot;sAMAccountName&quot; for
 * Microsoft Active Directory.</li>
 * <li>
 * <b>userObjectClass:</b>The objectClass value for user nodes below the
 * userBase. For example &quot;inetOrgPerson&quot; for Apache DS, or
 * &quot;user&quot; for Microsoft Active Directory.</li>
 **
 * <li>
 * <b>poolSize:</b> (optional, default = 4) The maximum number of connection in the pool. Note that if the pool is exhausted,
 * extra connections will be created on the fly as needed.</li>
 * <li><b>maxWaitTime</b>: (optional, default = 1000) the number of milli seconds to wait before creating off-pool
 * connections, using a pool connection if released in time. This effectively smooth out traffic burst, thus in some case can help
 * not overloading the LDAP</li>
 * <li>
 * </ul>
 * </p>
 *
 * <p>
 * In order to enable group/role based access restrictions, you can use the
 * &quot;&lt;restriction&gt;&quot; configuration element. An example of this is
 * shown below: <br>
 *
 * <pre>
 * &lt;restriction
 *  memberAttribute=&quot;uniqueMember&quot;&gt;
 *    &lt;group&gt;cn=PermanentStaff,ou=Groups,o=myorg.co.uk,ou=system&lt;/group&gt;
 *          &lt;group&gt;cn=TemporaryStaff,ou=Groups,o=myorg.co.uk,ou=system&lt;/group&gt;
 * &lt;/restriction&gt;
 * </pre>
 *
 * Its constituent attributes and elements are defined as follows:
 * <ul>
 * <li>
 * <b>memberAttribute:</b> The LDAP attribute whose values indicate the DNs of
 * the users which belong to the group or role.</li>
 * <li>
 * <b>group:</b> A valid group or role DN. A user is only authenticated
 * (permitted access) if they belong to at least one of the groups listed under
 * the &quot;&lt;restriction&gt;&quot; sections.</li>
 * </ul>
 * </p>
 * <p><b>WARNING</b>: group/role based access restrictions is currently untested and should
 * be considered experimental. Use at your own risks. Contributions to strengthen that part
 * of the code base are welcomed.</p>
 *
 * <p>
 * The following parameters may be used to adjust the underlying socket settings:
 * <ul>
 * <b>connectionTimeout:</b> (optional) Sets the connection timeout on the underlying  to the specified integer value
 * <li>
 * <b>readTimeout:</b> (optional) Sets property the read timeout to the specified integer value.
 * <li>
 * <b>administratorId:</b> (optional) User identifier of the administrator user.
 * The administrator user is allowed to authenticate as other users.
 * </ul>
 * </p>
 *
 * <p>
 * The <b>supportsVirtualHosting</b> tag allows you to define this repository as supporing
 * virtual hosting. For this LDAP repository, it means users will be looked for by their email
 * address instead of their unique identifier.
 * Generally to make it work, you need to configure <b>userIdAttribute</b> attribute to map
 * to a mail attribute such as <code>mail</code> instead of an unique id identifier.
 * </p>
 *
 * @see ReadOnlyLDAPUser
 * @see ReadOnlyLDAPGroupRestriction
 *
 */
public class ReadOnlyUsersLDAPRepository extends UsersRepositoryImpl<ReadOnlyLDAPUsersDAO> implements Configurable {
    private final LdapRepositoryConfiguration ldapConfiguration;

    @Inject
    public ReadOnlyUsersLDAPRepository(DomainList domainList,
                                       GaugeRegistry gaugeRegistry,
                                       LDAPConnectionPool ldapConnectionPool,
                                       LdapRepositoryConfiguration configuration) {
        super(domainList, new ReadOnlyLDAPUsersDAO(gaugeRegistry, ldapConnectionPool, configuration));
        this.ldapConfiguration = configuration;
    }

    public ReadOnlyUsersLDAPRepository(DomainList domainList,
                                       GaugeRegistry gaugeRegistry,
                                       LdapRepositoryConfiguration configuration) throws LDAPException {
        super(domainList, new ReadOnlyLDAPUsersDAO(gaugeRegistry, new LDAPConnectionFactory(configuration).getLdapConnectionPool(), configuration));
        this.ldapConfiguration = configuration;
    }

    /**
     * Extracts the parameters required by the repository instance from the
     * James server configuration data. The fields extracted include
     * {@link LdapRepositoryConfiguration#ldapHost}, {@link LdapRepositoryConfiguration#userIdAttribute}, {@link LdapRepositoryConfiguration#userBase},
     * {@link LdapRepositoryConfiguration#principal}, {@link LdapRepositoryConfiguration#credentials} and {@link LdapRepositoryConfiguration#restriction}.
     *
     * @param configuration
     *            An encapsulation of the James server configuration data.
     */
    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> configuration) throws ConfigurationException {
        super.configure(configuration);
    }

    /**
     * Initialises the user-repository instance. It will create a connection to
     * the LDAP host using the supplied configuration.
     *
     * @throws Exception
     *             If an error occurs authenticating or connecting to the
     *             specified LDAP host.
     */
    @PostConstruct
    public void init() throws Exception {
        usersDAO.init();
    }

    @Override
    public boolean supportVirtualHosting() {
        return ldapConfiguration.supportsVirtualHosting();
    }

    /**
     * Determines if the given username has administrator privileges.
     * <p>
     * If the {@code administratorId} is set in the LDAP configuration, the method will return
     * {@code true} only if the given username matches the configured administrator ID.
     * <p>
     * If the {@code administratorId} is not set, the method falls back to the default
     * administrator determination provided by the parent implementation which could
     * support a list of administrators.
     * </p>
     *
     * @param username The {@link Username} to check for administrator privileges.
     * @return {@code true} if the username has administrator privileges, {@code false} otherwise.
     * @throws UsersRepositoryException If an error occurs while validating the username.
     */
    @Override
    public boolean isAdministrator(Username username) throws UsersRepositoryException {
        assertValid(username);

        return ldapConfiguration.getAdministratorId()
            .map(ldapAdministratorAttribute -> ldapAdministratorAttribute.equals(username))
            .orElseGet(Throwing.supplier(() -> super.isAdministrator(username)));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public void assertValid(Username username) throws UsersRepositoryException {
        assertLocalPartValid(username);

        boolean localPartAsLoginUsernameSupported = ldapConfiguration.getResolveLocalPartAttribute().isPresent();
        if (!localPartAsLoginUsernameSupported) {
            assertDomainPartValid(username);
        }
    }

    @Override
    public Mono<Void> assertValidReactive(Username username) {
        try {
            assertLocalPartValid(username);
            boolean localPartAsLoginUsernameSupported = ldapConfiguration.getResolveLocalPartAttribute().isPresent();
            if (!localPartAsLoginUsernameSupported) {
                return assertDomainPartValidReactive(username);
            }
            return Mono.empty();
        } catch (InvalidUsernameException e) {
            return Mono.error(e);
        }
    }
}
