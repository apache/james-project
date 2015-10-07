/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.mailbox.acl;

import java.util.HashSet;
import java.util.Set;


/**
 * In memory {@link GroupMembershipResolver} implementation. There is no
 * persistence. You will get only what you add.
 * 
 */
public class SimpleGroupMembershipResolver implements GroupMembershipResolver {

    private static class Membership {
        private final String group;
        private final int hash;
        private final String user;

        public Membership(String user, String group) {
            super();
            this.group = group;
            this.user = user;

            final int PRIME = 31;
            this.hash = PRIME * this.group.hashCode() + this.user.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Membership) {
                Membership other = (Membership) o;
                return this.group == other.group || (this.group != null && this.group.equals(other.group)) && this.user == other.user || (this.user != null && this.user.equals(other.user));

            }
            return false;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public String toString() {
            return group + ": " + user;
        }

    }

    private Set<Membership> memberships = new HashSet<SimpleGroupMembershipResolver.Membership>(32);

    public void addMembership(String group, String user) {
        memberships.add(new Membership(user, group));
    }

    @Override
    public boolean isMember(String user, String group) {
        return memberships.contains(new Membership(user, group));
    }

}
