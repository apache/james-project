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

package org.apache.james.protocols.smtp;

import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.core.MailAddress;

/**
 * Human readable texts returned to the client upon rejection of a command.
 *
 * Those can be overridden by the administrator - typically in order to translate them - through the
 * {@code errorMessages} section of the server configuration:
 *
 * <pre>{@code
 * <errorMessages>
 *     <oversizedMail>Message size exceeds fixed maximum message size</oversizedMail>
 *     <unknownUser>The recipient does not exist.</unknownUser>
 * </errorMessages>
 * }</pre>
 *
 * Only the human readable part of the SMTP response is customizable: return codes and DSN statuses
 * are left untouched as remote servers do rely on them.
 */
public record SMTPErrorMessages(Optional<String> oversizedMail, Optional<String> unknownUser) {
    public static final String DEFAULT_OVERSIZED_MAIL = "Message size exceeds fixed maximum message size";
    public static final String DEFAULT_UNKNOWN_USER = "Unknown user:";

    public static final SMTPErrorMessages DEFAULT = new SMTPErrorMessages(Optional.empty(), Optional.empty());

    public static SMTPErrorMessages parse(Configuration configuration) {
        return new SMTPErrorMessages(
            Optional.ofNullable(configuration.getString("errorMessages.oversizedMail", null)),
            Optional.ofNullable(configuration.getString("errorMessages.unknownUser", null)));
    }

    /**
     * Text of the response rejecting a mail exceeding the maximum message size.
     */
    public String oversizedMailMessage() {
        return oversizedMail.orElse(DEFAULT_OVERSIZED_MAIL);
    }

    /**
     * Text of the response rejecting a recipient that does not exist.
     *
     * The rejected recipient is appended to the configured text, so that the client is always told which
     * of its recipients got rejected.
     */
    public String unknownUserMessage(MailAddress recipient) {
        return unknownUser.orElse(DEFAULT_UNKNOWN_USER) + " " + recipient.asString();
    }
}
