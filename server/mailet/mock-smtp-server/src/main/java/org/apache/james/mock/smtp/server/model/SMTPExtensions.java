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
import com.google.common.collect.ImmutableList;

public class SMTPExtensions {
    public static SMTPExtensions of(SMTPExtension... extensions) {
        return new SMTPExtensions(ImmutableList.copyOf(extensions));
    }

    private final List<SMTPExtension> smtpExtensions;

    @JsonCreator
    private SMTPExtensions(List<SMTPExtension> smtpExtensions) {
        this.smtpExtensions = ImmutableList.copyOf(smtpExtensions);
    }

    @JsonValue
    public List<SMTPExtension> getSmtpExtensions() {
        return smtpExtensions;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof SMTPExtensions) {
            SMTPExtensions that = (SMTPExtensions) o;

            return Objects.equals(this.smtpExtensions, that.smtpExtensions);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(smtpExtensions);
    }
}
