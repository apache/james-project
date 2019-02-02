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

import java.util.Objects;
import java.util.Optional;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;

import com.google.common.base.Preconditions;

public class LdapRepositoryConfiguration {
    public static final String SUPPORTS_VIRTUAL_HOSTING = "supportsVirtualHosting";

    private static final boolean USE_CONNECTION_POOL = true;
    private static final int NO_CONNECTION_TIMEOUT = -1;
    private static final int NO_READ_TIME_OUT = -1;
    private static final boolean ENABLE_VIRTUAL_HOSTING = true;
    private static final ReadOnlyLDAPGroupRestriction NO_RESTRICTION = new ReadOnlyLDAPGroupRestriction(null);
    private static final String NO_FILTER = null;
    private static final Optional<String> NO_ADMINISTRATOR_ID = Optional.empty();

    public static class Builder {
        private Optional<String> ldapHost;
        private Optional<String> principal;
        private Optional<String> credentials;
        private Optional<String> userBase;
        private Optional<String> userIdAttribute;
        private Optional<String> userObjectClass;
        private Optional<Integer> maxRetries;
        private Optional<Long> retryStartInterval;
        private Optional<Long> retryMaxInterval;
        private Optional<Integer> scale;

        public Builder() {
            ldapHost = Optional.empty();
            principal = Optional.empty();
            credentials = Optional.empty();
            userBase = Optional.empty();
            userIdAttribute = Optional.empty();
            userObjectClass = Optional.empty();
            maxRetries = Optional.empty();
            retryStartInterval = Optional.empty();
            retryMaxInterval = Optional.empty();
            scale = Optional.empty();
        }

        public Builder ldapHost(String ldapHost) {
            this.ldapHost = Optional.of(ldapHost);
            return this;
        }

        public Builder principal(String principal) {
            this.principal = Optional.of(principal);
            return this;
        }

        public Builder credentials(String credentials) {
            this.credentials = Optional.of(credentials);
            return this;
        }

        public Builder userBase(String userBase) {
            this.userBase = Optional.of(userBase);
            return this;
        }

        public Builder userIdAttribute(String userIdAttribute) {
            this.userIdAttribute = Optional.of(userIdAttribute);
            return this;
        }

        public Builder userObjectClass(String userObjectClass) {
            this.userObjectClass = Optional.of(userObjectClass);
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = Optional.of(maxRetries);
            return this;
        }

        public Builder retryStartInterval(long retryStartInterval) {
            this.retryStartInterval = Optional.of(retryStartInterval);
            return this;
        }

        public Builder retryMaxInterval(long retryMaxInterval) {
            this.retryMaxInterval = Optional.of(retryMaxInterval);
            return this;
        }

        public Builder scale(int scale) {
            this.scale = Optional.of(scale);
            return this;
        }

        public LdapRepositoryConfiguration build() throws ConfigurationException {
            Preconditions.checkState(ldapHost.isPresent(), "'ldapHost' is mandatory");
            Preconditions.checkState(principal.isPresent(), "'principal' is mandatory");
            Preconditions.checkState(credentials.isPresent(), "'credentials' is mandatory");
            Preconditions.checkState(userBase.isPresent(), "'userBase' is mandatory");
            Preconditions.checkState(userIdAttribute.isPresent(), "'userIdAttribute' is mandatory");
            Preconditions.checkState(userObjectClass.isPresent(), "'userObjectClass' is mandatory");
            Preconditions.checkState(maxRetries.isPresent(), "'maxRetries' is mandatory");
            Preconditions.checkState(retryStartInterval.isPresent(), "'retryStartInterval' is mandatory");
            Preconditions.checkState(retryMaxInterval.isPresent(), "'retryMaxInterval' is mandatory");
            Preconditions.checkState(scale.isPresent(), "'scale' is mandatory");

            return new LdapRepositoryConfiguration(
                ldapHost.get(),
                principal.get(),
                credentials.get(),
                userBase.get(),
                userIdAttribute.get(),
                userObjectClass.get(),
                USE_CONNECTION_POOL,
                NO_CONNECTION_TIMEOUT,
                NO_READ_TIME_OUT,
                maxRetries.get(),
                !ENABLE_VIRTUAL_HOSTING,
                retryStartInterval.get(),
                retryMaxInterval.get(),
                scale.get(),
                NO_RESTRICTION,
                NO_FILTER,
                NO_ADMINISTRATOR_ID);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LdapRepositoryConfiguration from(HierarchicalConfiguration configuration) throws ConfigurationException {
        String ldapHost = configuration.getString("[@ldapHost]", "");
        String principal = configuration.getString("[@principal]", "");
        String credentials = configuration.getString("[@credentials]", "");
        String userBase = configuration.getString("[@userBase]");
        String userIdAttribute = configuration.getString("[@userIdAttribute]");
        String userObjectClass = configuration.getString("[@userObjectClass]");
        // Default is to use connection pooling
        boolean useConnectionPool = configuration.getBoolean("[@useConnectionPool]", USE_CONNECTION_POOL);
        int connectionTimeout = configuration.getInt("[@connectionTimeout]", NO_CONNECTION_TIMEOUT);
        int readTimeout = configuration.getInt("[@readTimeout]", NO_READ_TIME_OUT);
        // Default maximum retries is 1, which allows an alternate connection to
        // be found in a multi-homed environment
        int maxRetries = configuration.getInt("[@maxRetries]", 1);
        boolean supportsVirtualHosting = configuration.getBoolean(SUPPORTS_VIRTUAL_HOSTING, !ENABLE_VIRTUAL_HOSTING);
        // Default retry start interval is 0 second
        long retryStartInterval = configuration.getLong("[@retryStartInterval]", 0);
        // Default maximum retry interval is 60 seconds
        long retryMaxInterval = configuration.getLong("[@retryMaxInterval]", 60);
        int scale = configuration.getInt("[@retryIntervalScale]", 1000); // seconds

        HierarchicalConfiguration restrictionConfig = null;
        // Check if we have a restriction we can use
        // See JAMES-1204
        if (configuration.containsKey("restriction[@memberAttribute]")) {
            restrictionConfig = configuration.configurationAt("restriction");
        }
        ReadOnlyLDAPGroupRestriction restriction = new ReadOnlyLDAPGroupRestriction(restrictionConfig);

        //see if there is a filter argument
        String filter = configuration.getString("[@filter]");

        Optional<String> administratorId = Optional.ofNullable(configuration.getString("[@administratorId]"));

        return new LdapRepositoryConfiguration(
            ldapHost,
            principal,
            credentials,
            userBase,
            userIdAttribute,
            userObjectClass,
            useConnectionPool,
            connectionTimeout,
            readTimeout,
            maxRetries,
            supportsVirtualHosting,
            retryStartInterval,
            retryMaxInterval,
            scale,
            restriction,
            filter,
            administratorId);
    }

    /**
     * The URL of the LDAP server against which users are to be authenticated.
     * Note that users are actually authenticated by binding against the LDAP
     * server using the users &quot;dn&quot; and &quot;credentials&quot;.The
     * value of this field is taken from the value of the configuration
     * attribute &quot;ldapHost&quot;.
     */
    private final String ldapHost;

    /**
     * The user with which to initially bind to the LDAP server. The value of
     * this field is taken from the configuration attribute
     * &quot;principal&quot;.
     */
    private final String principal;

    /**
     * The password/credentials with which to initially bind to the LDAP server.
     * The value of this field is taken from the configuration attribute
     * &quot;credentials&quot;.
     */
    private final String credentials;

    /**
     * This is the LDAP context/sub-context within which to search for user
     * entities. The value of this field is taken from the configuration
     * attribute &quot;userBase&quot;.
     */
    private final String userBase;

    /**
     * The value of this field is taken from the configuration attribute
     * &quot;userIdAttribute&quot;. This is the LDAP attribute type which holds
     * the userId value. Note that this is not the same as the email address
     * attribute.
     */
    private final String userIdAttribute;

    /**
     * The value of this field is taken from the configuration attribute
     * &quot;userObjectClass&quot;. This is the LDAP object class to use in the
     * search filter for user nodes under the userBase value.
     */
    private final String userObjectClass;

    // Use a connection pool. Default is true.
    private final boolean useConnectionPool;

    // The connection timeout in milliseconds.
    // A value of less than or equal to zero means to use the network protocol's
    // (i.e., TCP's) timeout value.
    private final int connectionTimeout;

    // The LDAP read timeout in milliseconds.
    private final int readTimeout;

    // Maximum number of times to retry a connection attempts. Default is no
    // retries.
    private final int maxRetries;
    private final boolean supportsVirtualHosting;
    private final long retryStartInterval;
    private final long retryMaxInterval;
    private final int scale;

    /**
     * Encapsulates the information required to restrict users to LDAP groups or
     * roles. This object is populated from the contents of the configuration
     * element &lt;restriction&gt;.
     */
    private final ReadOnlyLDAPGroupRestriction restriction;

    /**
     * The value of this field is taken from the configuration attribute &quot;filter&quot;.
     * This is the search filter to use to find the desired user.
     */
    private final String filter;

    /**
     * UserId of the administrator
     * The administrator is allowed to log in as other users
     */
    private final Optional<String> administratorId;

    private LdapRepositoryConfiguration(String ldapHost, String principal, String credentials, String userBase, String userIdAttribute,
                                       String userObjectClass, boolean useConnectionPool, int connectionTimeout, int readTimeout,
                                       int maxRetries, boolean supportsVirtualHosting, long retryStartInterval, long retryMaxInterval,
                                       int scale, ReadOnlyLDAPGroupRestriction restriction, String filter,
                                       Optional<String> administratorId) throws ConfigurationException {
        this.ldapHost = ldapHost;
        this.principal = principal;
        this.credentials = credentials;
        this.userBase = userBase;
        this.userIdAttribute = userIdAttribute;
        this.userObjectClass = userObjectClass;
        this.useConnectionPool = useConnectionPool;
        this.connectionTimeout = connectionTimeout;
        this.readTimeout = readTimeout;
        this.maxRetries = maxRetries;
        this.supportsVirtualHosting = supportsVirtualHosting;
        this.retryStartInterval = retryStartInterval;
        this.retryMaxInterval = retryMaxInterval;
        this.scale = scale;
        this.restriction = restriction;
        this.filter = filter;
        this.administratorId = administratorId;

        checkState();
    }

    private void checkState() throws ConfigurationException {
        if (userBase == null) {
            throw new ConfigurationException("[@userBase] is mandatory");
        }
        if (userIdAttribute == null) {
            throw new ConfigurationException("[@userIdAttribute] is mandatory");
        }
        if (userObjectClass == null) {
            throw new ConfigurationException("[@userObjectClass] is mandatory");
        }
    }

    public String getLdapHost() {
        return ldapHost;
    }

    public String getPrincipal() {
        return principal;
    }

    public String getCredentials() {
        return credentials;
    }

    public String getUserBase() {
        return userBase;
    }

    public String getUserIdAttribute() {
        return userIdAttribute;
    }

    public String getUserObjectClass() {
        return userObjectClass;
    }

    public boolean useConnectionPool() {
        return useConnectionPool;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public boolean supportsVirtualHosting() {
        return supportsVirtualHosting;
    }

    public long getRetryStartInterval() {
        return retryStartInterval;
    }

    public long getRetryMaxInterval() {
        return retryMaxInterval;
    }

    public int getScale() {
        return scale;
    }

    public ReadOnlyLDAPGroupRestriction getRestriction() {
        return restriction;
    }

    public String getFilter() {
        return filter;
    }

    public Optional<String> getAdministratorId() {
        return administratorId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof LdapRepositoryConfiguration) {
            LdapRepositoryConfiguration that = (LdapRepositoryConfiguration) o;

            return Objects.equals(this.useConnectionPool, that.useConnectionPool)
                && Objects.equals(this.connectionTimeout, that.connectionTimeout)
                && Objects.equals(this.readTimeout, that.readTimeout)
                && Objects.equals(this.maxRetries, that.maxRetries)
                && Objects.equals(this.supportsVirtualHosting, that.supportsVirtualHosting)
                && Objects.equals(this.retryStartInterval, that.retryStartInterval)
                && Objects.equals(this.retryMaxInterval, that.retryMaxInterval)
                && Objects.equals(this.scale, that.scale)
                && Objects.equals(this.ldapHost, that.ldapHost)
                && Objects.equals(this.principal, that.principal)
                && Objects.equals(this.credentials, that.credentials)
                && Objects.equals(this.userBase, that.userBase)
                && Objects.equals(this.userIdAttribute, that.userIdAttribute)
                && Objects.equals(this.userObjectClass, that.userObjectClass)
                && Objects.equals(this.restriction, that.restriction)
                && Objects.equals(this.filter, that.filter)
                && Objects.equals(this.administratorId, that.administratorId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ldapHost, principal, credentials, userBase, userIdAttribute, userObjectClass, useConnectionPool,
            connectionTimeout, readTimeout, maxRetries, supportsVirtualHosting, retryStartInterval, retryMaxInterval, scale,
            restriction, filter, administratorId);
    }
}
