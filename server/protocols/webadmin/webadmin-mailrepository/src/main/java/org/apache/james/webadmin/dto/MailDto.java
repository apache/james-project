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

package org.apache.james.webadmin.dto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;

import com.github.steveash.guavate.Guavate;

public class MailDto {
    public static MailDto fromMail(Mail mail) throws MessagingException {
        return new MailDto(mail.getName(),
            Optional.ofNullable(mail.getSender()).map(MailAddress::asString),
            mail.getRecipients().stream().map(MailAddress::asString).collect(Guavate.toImmutableList()),
            Optional.ofNullable(mail.getErrorMessage()),
            Optional.ofNullable(mail.getState()));
    }

    private final String name;
    private final Optional<String> sender;
    private final List<String> recipients;
    private final Optional<String> error;
    private final Optional<String> state;

    public MailDto(String name, Optional<String> sender, List<String> recipients, Optional<String> error,
                   Optional<String> state) {
        this.name = name;
        this.sender = sender;
        this.recipients = recipients;
        this.error = error;
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public Optional<String> getSender() {
        return sender;
    }

    public List<String> getRecipients() {
        return recipients;
    }

    public Optional<String> getError() {
        return error;
    }

    public Optional<String> getState() {
        return state;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailDto) {
            MailDto mailDto = (MailDto) o;

            return Objects.equals(this.name, mailDto.name)
                && Objects.equals(this.sender, mailDto.sender)
                && Objects.equals(this.recipients, mailDto.recipients)
                && Objects.equals(this.error, mailDto.error)
                && Objects.equals(this.state, mailDto.state);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(name, sender, recipients, error, state);
    }
}
