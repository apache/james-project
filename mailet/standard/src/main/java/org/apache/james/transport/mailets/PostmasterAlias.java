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

package org.apache.james.transport.mailets;

import java.util.Collection;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

/**
 * Rewrites recipient addresses to make sure email for the postmaster is
 * always handled.  This mailet is silently inserted at the top of the root
 * spool processor.  All recipients mapped to postmaster@<servernames> are
 * changed to the postmaster account as specified in the server conf.
 */
public class PostmasterAlias extends GenericMailet {

    /**
     * Make sure that a message that is addressed to a postmaster alias is always
     * sent to the postmaster address, regardless of delivery to other recipients.
     *
     * @param mail the mail to process
     *
     * @throws MessagingException if an error is encountered while modifying the message
     */
    @Override
    public void service(Mail mail) throws MessagingException {
        Collection<MailAddress> postmasterAliases = mail.getRecipients()
            .stream()
            .filter(this::isPostmasterAlias)
            .collect(ImmutableList.toImmutableList());

        if (!postmasterAliases.isEmpty()) {
            mail.setRecipients(
                Stream.concat(
                    mail.getRecipients()
                        .stream()
                        .filter(address -> !postmasterAliases.contains(address)),
                    Stream.of(getMailetContext().getPostmaster()))
                .collect(ImmutableSet.toImmutableSet()));
        }
    }

    private boolean isPostmasterAlias(MailAddress addr) {
        return addr.getLocalPart().equalsIgnoreCase("postmaster")
            && getMailetContext().isLocalServer(addr.getDomain())
            && !getMailetContext().isLocalEmail(addr);
    }

    @Override
    public String getMailetInfo() {
        return "Postmaster aliasing mailet";
    }
}
