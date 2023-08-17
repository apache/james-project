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

package org.apache.james.sieve.cassandra.model;

import java.util.Objects;
import java.util.Optional;

import org.apache.james.sieverepository.api.ScriptContent;
import org.apache.james.sieverepository.api.ScriptName;
import org.apache.james.sieverepository.api.ScriptSummary;

import com.google.common.base.Preconditions;

public class Script {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ScriptName name;
        private ScriptContent content;
        private Optional<Boolean> isActive = Optional.empty();
        private Optional<Long> size = Optional.empty();

        public Builder name(ScriptName name) {
            this.name = name;
            return this;
        }

        public Builder name(String name) {
            return this.name(new ScriptName(name));
        }

        public Builder copyOf(Script script) {
            this.name = script.getName();
            this.content = script.getContent();
            this.isActive = Optional.of(script.isActive());
            this.size = Optional.of(script.getSize());
            return this;
        }

        public Builder content(ScriptContent content) {
            this.content = content;
            return this;
        }

        public Builder content(String content) {
            return this.content(new ScriptContent(content));
        }

        public Builder size(long size) {
            this.size = Optional.of(size);
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = Optional.of(isActive);
            return this;
        }

        public Script build() {
            Preconditions.checkState(name != null);
            Preconditions.checkState(content != null);
            Preconditions.checkState(isActive.isPresent());

            return new Script(name,
                content,
                isActive.get(),
                size.orElse((long) content.length()));
        }

    }

    private final ScriptName name;
    private final ScriptContent content;
    private final boolean isActive;
    private final long size;

    private Script(ScriptName name, ScriptContent content, boolean isActive, long size) {
        this.name = name;
        this.content = content;
        this.isActive = isActive;
        this.size = size;
    }

    public ScriptName getName() {
        return name;
    }

    public ScriptContent getContent() {
        return content;
    }

    public boolean isActive() {
        return isActive;
    }

    public ScriptSummary toSummary() {
        return new ScriptSummary(name, isActive);
    }

    public long getSize() {
        return size;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Script) {
            Script that = (Script) o;

            return Objects.equals(this.name, that.name)
                && Objects.equals(this.content, that.content)
                && Objects.equals(this.isActive, that.isActive)
                && Objects.equals(this.size, that.size);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, content, isActive, size);
    }
}
