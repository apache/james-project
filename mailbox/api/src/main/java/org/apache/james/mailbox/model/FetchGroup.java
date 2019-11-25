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

import java.util.HashSet;
import java.util.Set;

/**
 * Indicates the results fetched.
 */
public class FetchGroup {
    /**
     * For example: could have best performance when doing store and then
     * forget. UIDs are always returned
     */
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

    private int content;

    private Set<PartContentDescriptor> partContentDescriptors;

    private FetchGroup(int content) {
        this(content, new HashSet<>());
    }

    private FetchGroup(int content, Set<PartContentDescriptor> partContentDescriptors) {
        this.content = content;
        this.partContentDescriptors = partContentDescriptors;
    }

    /**
     * Contents to be fetched. Composed bitwise.
     *
     * @return bitwise description
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

    public void or(int content) {
        this.content = this.content | content;
    }

    public String toString() {
        return "Fetch " + content;
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
    public void addPartContent(MimePath path, int content) {
        if (partContentDescriptors == null) {
            partContentDescriptors = new HashSet<>();
        }
        PartContentDescriptor currentDescriptor = partContentDescriptors.stream()
            .filter(descriptor -> path.equals(descriptor.path()))
            .findFirst()
            .orElseGet(() -> {
                PartContentDescriptor result = new PartContentDescriptor(path);
                partContentDescriptors.add(result);
                return result;
            });

        currentDescriptor.or(content);
    }
}
