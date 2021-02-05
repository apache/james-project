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

package org.apache.james.mailbox.elasticsearch.v7.json;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;

public class Subjects implements SerializableMessage {

    public static Subjects from(Set<String> subjects) {
        Preconditions.checkNotNull(subjects, "'subjects' is mandatory");
        return new Subjects(subjects);
    }

    private final Set<String> subjects;

    private Subjects(Set<String> subjects) {
        this.subjects = subjects;
    }

    @JsonValue
    public Set<String> getSubjects() {
        return subjects;
    }

    @Override
    public String serialize() {
        return Joiner.on(" ").join(subjects);
    }
}
