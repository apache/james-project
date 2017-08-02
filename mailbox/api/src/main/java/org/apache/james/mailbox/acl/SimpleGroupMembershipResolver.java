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

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;


/**
 * In memory {@link GroupMembershipResolver} implementation. There is no
 * persistence. You will get only what you add.
 * 
 */
public class SimpleGroupMembershipResolver implements GroupMembershipResolver {

    public static class Membership {
        private final String group;
        private final String user;

        public Membership(String user, String group) {
            this.group = group;
            this.user = user;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof Membership) {
                Membership that = (Membership) o;
                
                return Objects.equal(this.user, that.user) && Objects.equal(this.group, that.group);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(group, user);
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                .add("group", group)
                .add("user", user)
                .toString();
        }

    }

    private final Set<Membership> memberships = new HashSet<>(32);

    public void addMembership(String group, String user) {
        memberships.add(new Membership(user, group));
    }

    @Override
    public boolean isMember(String user, String group) {
        return memberships.contains(new Membership(user, group));
    }

}
