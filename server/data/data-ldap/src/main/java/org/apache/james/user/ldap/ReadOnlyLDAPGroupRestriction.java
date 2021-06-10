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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;

import com.github.fge.lambdas.Throwing;
import com.github.steveash.guavate.Guavate;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPConnectionPool;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.SearchResultEntry;

/**
 * <p>
 * Encapsulates the information required to restrict users to LDAP groups or
 * roles. Instances of this type are populated from the contents of the
 * <code>&lt;users-store&gt;</code> configuration child-element
 * <code>&lt;restriction&gt;<code>.
 * </p>
 *
 * @see ReadOnlyUsersLDAPRepository
 * @see ReadOnlyLDAPUser
 */

public class ReadOnlyLDAPGroupRestriction {
    /**
     * The name of the LDAP attribute name which holds the unique names
     * (distinguished-names/DNs) of the members of the group/role.
     */
    private String memberAttribute;

    /**
     * The distinguished-names of the LDAP groups/roles to which James users
     * must belong. A user who is not a member of at least one of the groups or
     * roles specified here will not be allowed to authenticate against James.
     * If the list is empty, group/role restriction will be disabled.
     */
    private final List<String> groupDNs;

    /**
     * Initialises an instance from the contents of a
     * <code>&lt;restriction&gt;<code> configuration XML
     * element.
     *
     * @param configuration The avalon configuration instance that encapsulates the
     *                      contents of the <code>&lt;restriction&gt;<code> XML element.
     * @throws org.apache.commons.configuration2.ex.ConfigurationException
     *          If an error occurs extracting values from the configuration
     *          element.
     */
    public ReadOnlyLDAPGroupRestriction(HierarchicalConfiguration<ImmutableNode> configuration) {
        groupDNs = new ArrayList<>();

        if (configuration != null) {
            memberAttribute = configuration.getString("[@memberAttribute]");

            if (configuration.getKeys("group").hasNext()) {
                Collections.addAll(groupDNs, configuration.getStringArray("group"));
            }
        }
    }

    /**
     * Indicates if group/role-based restriction is enabled for the the
     * user-store, based on the information encapsulated in the instance.
     *
     * @return <code>True</code> If there list of group/role distinguished names
     *         is not empty, and <code>false</code> otherwise.
     */
    protected boolean isActivated() {
        return !groupDNs.isEmpty();
    }

    /**
     * Converts an instance of this type to a string.
     *
     * @return A string representation of the instance.
     */
    public String toString() {
        return "Activated=" + isActivated() + "; Groups=" + groupDNs;
    }

    /**
     * Returns the distinguished-names (DNs) of all the members of the groups
     * specified in the restriction list. The information is organised as a list
     * of <code>&quot;&lt;groupDN&gt;=&lt;
     * [userDN1,userDN2,...,userDNn]&gt;&quot;</code>. Put differently, each
     * <code>groupDN</code> is associated to a list of <code>userDNs</code>.
     *
     * @return Returns a map of groupDNs to userDN lists.
     */
    protected Map<String, Collection<DN>> getGroupMembershipLists(LDAPConnectionPool connection) throws LDAPException {
        Map<String, Collection<DN>> result = new HashMap<>();

        for (String groupDN : groupDNs) {
            result.put(groupDN, extractMembers(connection.getEntry(groupDN)));
        }

        return result;
    }

    /**
     * Extracts the DNs for members of the group with the given LDAP context
     * attributes. This is achieved by extracting all the values of the LDAP
     * attribute, with name equivalent to the field value
     * {@link #memberAttribute}, from the attributes collection.
     *
     * @return A collection of distinguished-names for the users belonging to
     *         the group with the specified attributes.
     */
    private Collection<DN> extractMembers(SearchResultEntry entry) {
        com.unboundid.ldap.sdk.Attribute members = entry.getAttribute(memberAttribute);
        return Arrays.stream(members.getValues())
            .map(Throwing.function(DN::new))
            .collect(Guavate.toImmutableList());
    }
}
