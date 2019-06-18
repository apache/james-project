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
package org.apache.james.transport.util;

import java.util.Optional;

import org.apache.james.core.MailAddress;
import org.apache.james.transport.mailets.redirect.SpecialAddress;
import org.apache.mailet.Mail;

public class ReplyToUtils {

    public static ReplyToUtils from(Optional<MailAddress> replyTo) {
        return new ReplyToUtils(replyTo);
    }

    public static ReplyToUtils from(MailAddress replyTo) {
        return new ReplyToUtils(Optional.ofNullable(replyTo));
    }

    private final Optional<MailAddress> replyTo;

    private ReplyToUtils(Optional<MailAddress> replyTo) {
        this.replyTo = replyTo;
    }

    public Optional<MailAddress> getReplyTo(Mail originalMail) {
        if (replyTo.isPresent()) {
            if (replyTo.get().equals(SpecialAddress.UNALTERED)) {
                return Optional.empty();
            }
            return originalMail.getMaybeSender().asOptional();
        }
        return Optional.empty();
    }
}
