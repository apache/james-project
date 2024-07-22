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

package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.user.ldap.LDAPConnectionFactory;
import org.apache.james.user.ldap.LdapRepositoryConfiguration;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResultEntry;

/**
 * Matchers that allow looking up for LDAP group membership for the sender.
 *
 * To enable this matcher one need first to add the james-server-mailet-ldap.jar in the externals-jars folder of your
 * James installation.
 *
 * In order to match the group membership:
 *
 * <pre><code>
 * &lt;mailet matcher=&quot;SenderIsInLDAPGroup=cn=mygroup,ou=groups, dc=james,dc=org&quot; class=&quot;Null&quot;&gt;
 *
 * &lt;/mailet&gt;
 * </code></pre>
 */
public class SenderIsInLDAPGroup extends GenericMatcher {
    private final LDAPConnectionPool ldapConnectionPool;
    private final LdapRepositoryConfiguration configuration;
    private String groupDN;

    @Inject
    public SenderIsInLDAPGroup(LDAPConnectionPool ldapConnectionPool, LdapRepositoryConfiguration configuration) {
        this.configuration = configuration;
        this.ldapConnectionPool = ldapConnectionPool;
    }

    public SenderIsInLDAPGroup(LdapRepositoryConfiguration configuration) throws LDAPException {
        this(new LDAPConnectionFactory(configuration).getLdapConnectionPool(), configuration);
    }

    @Override
    public void init() {
        groupDN = getCondition();
    }

    @Override
    public Collection<MailAddress> match(Mail mail) {
        if (mail.getMaybeSender().asOptional().map(a -> groupMembers().contains(a)).orElse(false)) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private Set<MailAddress> groupMembers() {
        try {
            String[] attributes = {"member"};
            SearchResultEntry groupEntry = ldapConnectionPool.getEntry(groupDN, attributes);

            if (groupEntry == null) {
                return ImmutableSet.of();
            }

            return groupEntry.getAttributes().stream()
                .filter(a -> a.getName().equals("member"))
                .map(Attribute::getValue)
                .map(Throwing.function(memberDn -> Optional.ofNullable(ldapConnectionPool.getEntry(memberDn, configuration.getReturnedAttributes()))))
                .flatMap(Optional::stream)
                .map(memberEntry -> memberEntry.getAttribute(configuration.getUserIdAttribute()).getValue())
                .map(Throwing.function(MailAddress::new))
                .collect(ImmutableSet.toImmutableSet());
        } catch (LDAPException e) {
            throw new RuntimeException("Failed searching LDAP", e);
        }
    }
}
