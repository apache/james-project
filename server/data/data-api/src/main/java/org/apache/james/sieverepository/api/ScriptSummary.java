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

package org.apache.james.sieverepository.api;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

public class ScriptSummary {

    private final ScriptName name;
    private final boolean activeFile;

    private final long size;

    public ScriptSummary(ScriptName name, boolean activeFile, long size) {
        this.name = name;
        this.activeFile = activeFile;
        this.size = size;
    }

    public ScriptName getName() {
        return name;
    }

    public boolean isActive() {
        return activeFile;
    }

    public long getSize() {
        return size;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ScriptSummary) {
            ScriptSummary that = (ScriptSummary) o;

            return Objects.equal(this.name, that.name)
                && Objects.equal(this.activeFile, that.activeFile)
                && Objects.equal(this.size, that.size);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(name, activeFile, size);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("name",  name)
            .add("activeFile", activeFile)
            .add("size", size)
            .toString();
    }
}