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

import java.util.EnumSet;
import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

/**
 * Describes the contents to be fetched for a mail part. All
 * implementations MUST implement equals. Two implementations are equal
 * if and only if their paths are equal.
 */
public class PartContentDescriptor {
    private final EnumSet<FetchGroup.Profile> content;
    private final MimePath path;

    public PartContentDescriptor(MimePath path) {
        this(EnumSet.noneOf(FetchGroup.Profile.class), path);
    }

    public PartContentDescriptor(EnumSet<FetchGroup.Profile> content, MimePath path) {
        this.content = content;
        this.path = path;
    }

    public PartContentDescriptor with(FetchGroup.Profile... profiles) {
        Preconditions.checkArgument(profiles.length > 0);
        return with(EnumSet.copyOf(ImmutableSet.copyOf(profiles)));
    }

    public PartContentDescriptor with(EnumSet<FetchGroup.Profile> profiles) {
        EnumSet<FetchGroup.Profile> result = EnumSet.noneOf(FetchGroup.Profile.class);
        result.addAll(this.content);
        result.addAll(profiles);
        return new PartContentDescriptor(result, path);
    }

    /**
     * Profiles to be fetched.
     *
     * @return Return an enumset of profiles to be fetched
     * @see FetchGroup.Profile
     */
    public EnumSet<FetchGroup.Profile> profiles() {
        return content;
    }

    /**
     * Path describing the part to be fetched.
     *
     * @return path describing the part, not null
     */
    public MimePath path() {
        return path;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof PartContentDescriptor) {
            PartContentDescriptor that = (PartContentDescriptor) obj;
            return Objects.equals(this.path, that.path);
        }
        return false;
    }

}
