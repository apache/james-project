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

package org.apache.james.mock.smtp.server.model;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class Mails {
    private final List<Mail> mailList;

    @JsonCreator
    public Mails(List<Mail> mailList) {
        this.mailList = mailList;
    }

    @JsonValue
    public List<Mail> getMailList() {
        return mailList;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Mails that) {

            return Objects.equals(this.mailList, that.mailList);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailList);
    }
}
