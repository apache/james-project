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
package org.apache.james.transport.matchers;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMatcher;

/**
 * Matches mail where the sender is local.
 */
public class SenderIsLocal extends GenericMatcher {

    @Override
    public final Collection<MailAddress> match(Mail mail) {
        if (mail.getMaybeSender().asOptional()
                .map(this::isLocal)
                .orElse(false)) {
            return mail.getRecipients();
        }
        return null;
    }

    private boolean isLocal(MailAddress mailAddress) {
        return getMailetContext().isLocalEmail(mailAddress);
    }

}
