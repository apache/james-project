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
import java.util.Set;
import java.util.stream.Stream;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

/**
 * Indicates the results fetched.
 */
public class FetchGroup extends Profiles<FetchGroup> {
    public enum Profile {
        MIME_DESCRIPTOR,
        HEADERS,
        FULL_CONTENT,
        BODY_CONTENT,
        MIME_HEADERS,
        MIME_CONTENT;
    }

    /**
     * For example: could have best performance when doing store and then
     * forget. UIDs are always returned
     */
    public static final FetchGroup MINIMAL = new FetchGroup(EnumSet.noneOf(Profile.class));
    public static final FetchGroup HEADERS = new FetchGroup(EnumSet.of(Profile.HEADERS));
    public static final FetchGroup FULL_CONTENT = new FetchGroup(EnumSet.of(Profile.FULL_CONTENT));
    public static final FetchGroup BODY_CONTENT = new FetchGroup(EnumSet.of(Profile.BODY_CONTENT));

    private final ImmutableSet<PartContentDescriptor> partContentDescriptors;

    @VisibleForTesting
    FetchGroup(EnumSet<Profile> content) {
        this(content, ImmutableSet.of());
    }

    @VisibleForTesting
    FetchGroup(EnumSet<Profile> content, ImmutableSet<PartContentDescriptor> partContentDescriptors) {
        super(content);
        this.partContentDescriptors = partContentDescriptors;
    }

    @Override
    FetchGroup copyWith(EnumSet<Profile> profiles) {
        return new FetchGroup(profiles, partContentDescriptors);
    }

    /**
     * Gets contents to be fetched for contained parts. For each part to be
     * contained, only one descriptor should be contained.
     *
     * @return <code>Set</code> of {@link PartContentDescriptor}, or null if
     *         there is no part content to be fetched
     */
    public Set<PartContentDescriptor> getPartContentDescriptors() {
        return partContentDescriptors;
    }

    /**
     * Adds profiles for the particular part.
     * 
     * @param path
     *            <code>MimePath</code>, not null
     * @param profiles
     *            bitwise profiles constant
     */
    public FetchGroup addPartContent(MimePath path, EnumSet<Profile> profiles) {
        PartContentDescriptor newContent = retrieveUpdatedPartContentDescriptor(path, profiles);

        return new FetchGroup(profiles(),
            Stream.concat(
                partContentDescriptors.stream()
                    .filter(descriptor -> !descriptor.path().equals(path)),
                Stream.of(newContent))
                .collect(Guavate.toImmutableSet()));
    }

    private PartContentDescriptor retrieveUpdatedPartContentDescriptor(MimePath path, EnumSet<Profile> profiles) {
        return partContentDescriptors.stream()
                .filter(descriptor -> path.equals(descriptor.path()))
                .findFirst()
                .orElse(new PartContentDescriptor(profiles, path));
    }

    @Override
    public String toString() {
        return "Fetch " + profiles();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FetchGroup) {
            FetchGroup that = (FetchGroup) o;

            return Objects.equals(this.profiles(), that.profiles())
                && Objects.equals(this.partContentDescriptors, that.partContentDescriptors);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(profiles(), partContentDescriptors);
    }
}
