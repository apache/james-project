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

package org.apache.james.jmap.model;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

public class Header {

    public static Builder builder() {
        return new Builder();
    }

    @JsonCreator
    public static Header from(List<String> header) {
        return builder().header(header).build();
    }

    public static class Builder {
        
        private String name;
        private Optional<String> value;

        public Builder header(List<String> header) {
            Preconditions.checkNotNull(header);
            Preconditions.checkArgument(header.size() > 0, "'header' should contains at least one element");
            Preconditions.checkArgument(header.size() < 3, "'header' should contains lesser than three elements");
            this.name = header.get(0);
            this.value = Optional.ofNullable(Iterables.get(header, 1, null));
            return this;
        }

        public Header build() {
            Preconditions.checkState(!Strings.isNullOrEmpty(name), "'name' is mandatory");
            return new Header(name, value);
        }
    }

    private final String name;
    private final Optional<String> value;

    private Header(String name, Optional<String> value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getValue() {
        return value;
    }
}
