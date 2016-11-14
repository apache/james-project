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

import java.util.List;

import javax.mail.MessagingException;

import org.apache.james.transport.mailets.redirect.AbstractRedirect;
import org.apache.james.transport.mailets.redirect.AddressExtractor;
import org.apache.mailet.MailAddress;

import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

public class SenderUtils {

    public static SenderUtils from(AbstractRedirect mailet) {
        return new SenderUtils(mailet);
    }

    private final AbstractRedirect mailet;

    private SenderUtils(AbstractRedirect mailet) {
        this.mailet = mailet;
    }

    public MailAddress getSender() throws MessagingException {
        String sender = mailet.getInitParameters().getSender();
        if (Strings.isNullOrEmpty(sender)) {
            return null;
        }

        List<MailAddress> extractAddresses = AddressExtractor
                .withContext(mailet.getMailetContext())
                .allowedSpecials(ImmutableList.of("postmaster", "sender", "unaltered"))
                .extract(sender);
        return FluentIterable.from(extractAddresses).first().orNull();
    }
}
