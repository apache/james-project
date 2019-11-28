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

package org.apache.james.mailbox.model;

import java.util.Collection;
import java.util.EnumSet;

import org.apache.james.mailbox.model.FetchGroup.Profile;

import com.google.common.collect.ImmutableSet;

public abstract class Profiles<T extends Profiles<T>> {
    private final EnumSet<Profile> profiles;

    public Profiles(EnumSet<Profile> profiles) {
        this.profiles = profiles;
    }

    /**
     * Profiles to be fetched.
     *
     * @return Return an enumset of profiles to be fetched
     * @see Profile
     */
    public EnumSet<Profile> profiles() {
        return EnumSet.copyOf(profiles);
    }

    public T with(Profile... profiles) {
        return with(EnumSet.copyOf(ImmutableSet.copyOf(profiles)));
    }

    public T with(Collection<Profile> profiles) {
        EnumSet<Profile> result = EnumSet.noneOf(Profile.class);
        result.addAll(this.profiles);
        result.addAll(profiles);
        return copyWith(result);
    }

    abstract T copyWith(EnumSet<Profile> profiles);
}
