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
import java.util.Iterator;
import java.util.Set;

import org.apache.james.mailbox.model.MessageResult.MimePath;

/**
 * Specifies a fetch group.
 */
public class FetchGroupImpl implements MessageResult.FetchGroup {

    public static final MessageResult.FetchGroup MINIMAL = new FetchGroupImpl(MessageResult.FetchGroup.MINIMAL);

    public static final MessageResult.FetchGroup HEADERS = new FetchGroupImpl(MessageResult.FetchGroup.HEADERS);

    public static final MessageResult.FetchGroup FULL_CONTENT = new FetchGroupImpl(MessageResult.FetchGroup.FULL_CONTENT);

    public static final MessageResult.FetchGroup BODY_CONTENT = new FetchGroupImpl(MessageResult.FetchGroup.BODY_CONTENT);

    private int content = MessageResult.FetchGroup.MINIMAL;

    private Set<PartContentDescriptor> partContentDescriptors;

    public FetchGroupImpl() {
        super();
    }

    public FetchGroupImpl(int content) {
        super();
        this.content = content;
    }

    public FetchGroupImpl(int content, Set<PartContentDescriptor> partContentDescriptors) {
        super();
        this.content = content;
        this.partContentDescriptors = partContentDescriptors;
    }

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
     * Gets content descriptors for the parts to be fetched.
     * 
     * @return <code>Set</code> of {@link org.apache.james.mailbox.MessageResult.FetchGroup.PartContentDescriptor},
     *         possibly null
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
            partContentDescriptors = new HashSet<PartContentDescriptor>();
        }
        PartContentDescriptorImpl currentDescriptor = null;
        for (Iterator<PartContentDescriptor> it = partContentDescriptors.iterator(); it.hasNext();) {
            PartContentDescriptor descriptor = (PartContentDescriptor) it.next();
            if (path.equals(descriptor.path())) {
                currentDescriptor = (PartContentDescriptorImpl) descriptor;
                break;
            }
        }
        if (currentDescriptor == null) {
            currentDescriptor = new PartContentDescriptorImpl(path);
            partContentDescriptors.add(currentDescriptor);
        }

        currentDescriptor.or(content);
    }
}
