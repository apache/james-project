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
import java.util.Objects;

/**
 * Describes the contents to be fetched for a mail part. All
 * implementations MUST implement equals. Two implementations are equal
 * if and only if their paths are equal.
 */
public class PartContentDescriptor extends Profiles<PartContentDescriptor> {
    private final MimePath path;

    public PartContentDescriptor(MimePath path) {
        this(EnumSet.noneOf(FetchGroup.Profile.class), path);
    }

    public PartContentDescriptor(Collection<FetchGroup.Profile> content, MimePath path) {
        super(content);
        this.path = path;
    }

    @Override
    protected PartContentDescriptor copyWith(Collection<FetchGroup.Profile> enumSet) {
        return new PartContentDescriptor(enumSet, path);
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
