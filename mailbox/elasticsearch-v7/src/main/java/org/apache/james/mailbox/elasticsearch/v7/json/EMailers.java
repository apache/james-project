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
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public class EMailers implements SerializableMessage {

    public static EMailers from(Set<EMailer> emailers) {
        Preconditions.checkNotNull(emailers, "'emailers' is mandatory");
        return new EMailers(emailers);
    }

    private final Set<EMailer> emailers;

    private EMailers(Set<EMailer> emailers) {
        this.emailers = emailers;
    }

    @JsonValue
    public Set<EMailer> getEmailers() {
        return emailers;
    }

    @Override
    public String serialize() {
        return emailers.stream()
            .map(EMailer::serialize)
            .collect(Collectors.joining(" "));
    }
}
