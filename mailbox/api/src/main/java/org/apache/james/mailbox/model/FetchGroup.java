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

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.github.steveash.guavate.Guavate;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;

/**
 * Indicates the results fetched.
 */
public class FetchGroup {
    /**
     * For example: could have best performance when doing store and then
     * forget. UIDs are always returned
     */
    public static final int NO_MASK = 0;
    public static final int MINIMAL_MASK = 0x00;
    public static final int MIME_DESCRIPTOR_MASK = 0x01;
    public static final int HEADERS_MASK = 0x100;
    public static final int FULL_CONTENT_MASK = 0x200;
    public static final int BODY_CONTENT_MASK = 0x400;
    public static final int MIME_HEADERS_MASK = 0x800;
    public static final int MIME_CONTENT_MASK = 0x1000;

    public static final FetchGroup MINIMAL = new FetchGroup(MINIMAL_MASK);
    public static final FetchGroup HEADERS = new FetchGroup(HEADERS_MASK);
    public static final FetchGroup FULL_CONTENT = new FetchGroup(FULL_CONTENT_MASK);
    public static final FetchGroup BODY_CONTENT = new FetchGroup(BODY_CONTENT_MASK);

    private final int content;
    private final ImmutableSet<PartContentDescriptor> partContentDescriptors;

    @VisibleForTesting
    FetchGroup(int content) {
        this(content, ImmutableSet.of());
    }

    @VisibleForTesting
    FetchGroup(int content, ImmutableSet<PartContentDescriptor> partContentDescriptors) {
        this.content = content;
        this.partContentDescriptors = partContentDescriptors;
    }

    /**
     * Contents to be fetched. Composed bitwise.
     *
     * @return masks to be used for bitewise operations.
     * @see #MINIMAL_MASK
     * @see #MIME_DESCRIPTOR_MASK
     * @see #HEADERS_MASK
     * @see #FULL_CONTENT_MASK
     * @see #BODY_CONTENT_MASK
     * @see #MIME_HEADERS_MASK
     * @see #MIME_CONTENT_MASK
     */
    public int content() {
        return content;
    }

    public FetchGroup with(int content) {
         return new FetchGroup(this.content | content, partContentDescriptors);
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
     * Adds content for the particular part.
     * 
     * @param path
     *            <code>MimePath</code>, not null
     * @param content
     *            bitwise content constant
     */
    public FetchGroup addPartContent(MimePath path, int content) {
        PartContentDescriptor newContent = retrieveUpdatedPartContentDescriptor(path, content);

        return new FetchGroup(this.content,
            Stream.concat(
                partContentDescriptors.stream()
                    .filter(descriptor -> !descriptor.path().equals(path)),
                Stream.of(newContent))
                .collect(Guavate.toImmutableSet()));
    }

    private PartContentDescriptor retrieveUpdatedPartContentDescriptor(MimePath path, int content) {
        return partContentDescriptors.stream()
                .filter(descriptor -> path.equals(descriptor.path()))
                .findFirst()
                .orElse(new PartContentDescriptor(path))
                .or(content);
    }

    public boolean hasMask(int mask) {
        return (content & mask) > NO_MASK;
    }

    public boolean hasOnlyMasks(int... masks) {
        int allowedMask = Arrays.stream(masks)
            .reduce((a, b) -> a | b)
            .orElse(0);
        return (content & (~ allowedMask)) == 0;
    }

    @Override
    public String toString() {
        return "Fetch " + content;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof FetchGroup) {
            FetchGroup that = (FetchGroup) o;

            return Objects.equals(this.content, that.content)
                && Objects.equals(this.partContentDescriptors, that.partContentDescriptors);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(content, partContentDescriptors);
    }
}
