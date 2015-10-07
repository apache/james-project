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

import org.apache.james.mailbox.model.MessageResult.FetchGroup.PartContentDescriptor;
import org.apache.james.mailbox.model.MessageResult.MimePath;


public class PartContentDescriptorImpl implements PartContentDescriptor {

    private int content = 0;

    private final MimePath path;

    public PartContentDescriptorImpl(final MimePath path) {
        super();
        this.path = path;
    }

    public PartContentDescriptorImpl(int content, final MimePath path) {
        super();
        this.content = content;
        this.path = path;
    }

    public void or(int content) {
        this.content = this.content | content;
    }

    public int content() {
        return content;
    }

    public MimePath path() {
        return path;
    }

    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + ((path == null) ? 0 : path.hashCode());
        return result;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final PartContentDescriptor other = (PartContentDescriptor) obj;
        if (path == null) {
            if (other.path() != null)
                return false;
        } else if (!path.equals(other.path()))
            return false;
        return true;
    }

}
