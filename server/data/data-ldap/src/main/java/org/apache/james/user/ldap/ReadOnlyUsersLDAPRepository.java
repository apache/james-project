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

import javax.annotation.PostConstruct;
import javax.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.lib.UsersRepositoryImpl;

/**
 * <p>
 * This repository implementation serves as a bridge between Apache James and
 * LDAP. It allows James to authenticate users against an LDAP compliant server
 * such as Apache DS or Microsoft AD. It also enables role/group based access
 * restriction based on LDAP groups.
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
 *      maxRetries=&quot;20&quot;
 *      retryStartInterval=&quot;0&quot;
 *      retryMaxInterval=&quot;30&quot;
 *      retryIntervalScale=&quot;1000&quot;
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
 * <b>maxRetries:</b> (optional, default = 0) The maximum number of times to
 * retry a failed operation. -1 means retry forever.</li>
 * <li>
 * <b>retryStartInterval:</b> (optional, default = 0) The interval in
 * milliseconds to wait before the first retry. If > 0, subsequent retries are
 * made at double the proceeding one up to the <b>retryMaxInterval</b> described
 * below. If = 0, the next retry is 1 and subsequent retries proceed as above.</li>
 * <li>
 * <b>retryMaxInterval:</b> (optional, default = 60) The maximum interval in
 * milliseconds to wait between retries</li>
 * <li>
 * <b>retryIntervalScale:</b> (optional, default = 1000) The amount by which to
 * multiply each retry interval. The default value of 1000 (milliseconds) is 1
 * second, so the default <b>retryMaxInterval</b> of 60 is 60 seconds, or 1
 * minute.
 * </ul>
 * </p>
 * <p>
 * <em>Example Schedules</em>
 * <ul>
 * <li>
 * Retry after 1000 milliseconds, doubling the interval for each retry up to
 * 30000 milliseconds, subsequent retry intervals are 30000 milliseconds until
 * 10 retries have been attempted, after which the <code>Exception</code>
 * causing the fault is thrown:
 * <ul>
 * <li>maxRetries = 10
 * <li>retryStartInterval = 1000
 * <li>retryMaxInterval = 30000
 * <li>retryIntervalScale = 1
 * </ul>
 * <li>
 * Retry immediately, then retry after 1 * 1000 milliseconds, doubling the
 * interval for each retry up to 30 * 1000 milliseconds, subsequent retry
 * intervals are 30 * 1000 milliseconds until 20 retries have been attempted,
 * after which the <code>Exception</code> causing the fault is thrown:
 * <ul>
 * <li>maxRetries = 20
 * <li>retryStartInterval = 0
 * <li>retryMaxInterval = 30
 * <li>retryIntervalScale = 1000
 * </ul>
 * <li>
 * Retry after 5000 milliseconds, subsequent retry intervals are 5000
 * milliseconds. Retry forever:
 * <ul>
 * <li>maxRetries = -1
 * <li>retryStartInterval = 5000
 * <li>retryMaxInterval = 5000
 * <li>retryIntervalScale = 1
 * </ul>
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
 *
 * <p>
 * The following parameters may be used to adjust the underlying
 * <code>com.sun.jndi.ldap.LdapCtxFactory</code>. See <a href=
 * "http://docs.oracle.com/javase/1.5.0/docs/guide/jndi/jndi-ldap.html#SPIPROPS"
 * > LDAP Naming Service Provider for the Java Naming and Directory InterfaceTM
 * (JNDI) : Provider-specific Properties</a> for details.
 * <ul>
 * <li>
 * <b>useConnectionPool:</b> (optional, default = true) Sets property
 * <code>com.sun.jndi.ldap.connect.pool</code> to the specified boolean value
 * <li>
 * <b>connectionTimeout:</b> (optional) Sets property
 * <code>com.sun.jndi.ldap.connect.timeout</code> to the specified integer value
 * <li>
 * <b>readTimeout:</b> (optional) Sets property
 * <code>com.sun.jndi.ldap.read.timeout</code> to the specified integer value.
 * Applicable to Java 6 and above.
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
    private LdapRepositoryConfiguration ldapConfiguration;

    @Inject
    public ReadOnlyUsersLDAPRepository(DomainList domainList) {
        super(domainList, new ReadOnlyLDAPUsersDAO());
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
        configure(LdapRepositoryConfiguration.from(configuration));
        super.configure(configuration);
    }

    public void configure(LdapRepositoryConfiguration configuration) {
        usersDAO.configure(configuration);
        this.ldapConfiguration = configuration;
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

    @Override
    public boolean isAdministrator(Username username) throws UsersRepositoryException {
        assertValid(username);

        if (ldapConfiguration.getAdministratorId().isPresent()) {
            return ldapConfiguration.getAdministratorId().get().equals(username);
        }
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
}
