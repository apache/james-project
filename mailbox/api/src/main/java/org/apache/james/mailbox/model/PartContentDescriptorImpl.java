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

import java.util.Objects;

import org.apache.james.mailbox.model.MessageResult.FetchGroup.PartContentDescriptor;

public class PartContentDescriptorImpl implements PartContentDescriptor {

    private int content = 0;

    private final MimePath path;

    public PartContentDescriptorImpl(MimePath path) {
        super();
        this.path = path;
    }

    public PartContentDescriptorImpl(int content, MimePath path) {
        super();
        this.content = content;
        this.path = path;
    }

    public void or(int content) {
        this.content = this.content | content;
    }

    @Override
    public int content() {
        return content;
    }

    @Override
    public MimePath path() {
        return path;
    }

    public int hashCode() {
        return Objects.hash(path);
    }

    public boolean equals(Object obj) {
        if (obj instanceof PartContentDescriptorImpl) {
            PartContentDescriptorImpl that = (PartContentDescriptorImpl) obj;
            return Objects.equals(this.path, that.path);
        }
        return false;
    }

}
