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

package org.apache.james.jmap.draft.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.methods.Method;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class SetVacationResponse implements Method.Response {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<String> updatedId = Optional.empty();
        private Optional<String> notUpdatedId = Optional.empty();
        private Optional<SetError> setError = Optional.empty();

        public Builder updatedId(String updatedId) {
            Preconditions.checkNotNull(updatedId);
            this.updatedId = Optional.of(updatedId);
            return this;
        }

        public Builder notUpdated(String id, SetError setError) {
            Preconditions.checkNotNull(id);
            Preconditions.checkNotNull(setError);
            this.notUpdatedId = Optional.of(id);
            this.setError = Optional.of(setError);
            return this;
        }

        public SetVacationResponse build() {
            return new SetVacationResponse(
                updatedId.map(ImmutableList::of),
                notUpdatedId.map(id -> ImmutableMap.of(id, setError.get())));
        }
    }

    private final Optional<List<String>> updated;
    private final Optional<Map<String, SetError>> notUpdated;

    private SetVacationResponse(Optional<List<String>> updated, Optional<Map<String, SetError>> notUpdated) {
        this.updated = updated;
        this.notUpdated = notUpdated;
    }

    public Optional<List<String>> getUpdated() {
        return updated;
    }

    public Optional<Map<String, SetError>> getNotUpdated() {
        return notUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SetVacationResponse that = (SetVacationResponse) o;

        return Objects.equals(this.updated, that.updated)
            && Objects.equals(this.notUpdated, that.notUpdated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(updated, notUpdated);
    }
}
