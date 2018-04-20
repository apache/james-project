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

import javax.inject.Inject;
import javax.mail.MessagingException;

import org.apache.james.domainlist.api.DomainList;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.user.api.UsersRepository;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Preconditions;

/**
 * Receives a Mail from JamesSpoolManager and takes care of delivery of the
 * message to local inboxes.
 * 
 * Available configurations are:
 * 
 * <code>&lt;enableAliases&gt;true&lt;/enableAliases&gt;</code>: specify wether
 * the user aliases should be looked up or not. Default is false.
 * 
 * <code>&lt;enableForwarding&gt;true&lt;/enableForwarding&gt;</code>: enable
 * the forwarding. Default to false.
 * 
 * 
 * @deprecated use org.apache.james.transport.mailets.RecipientRewriteTable
 */
@Experimental
@Deprecated
public class UsersRepositoryAliasingForwarding extends GenericMailet {
    private final UsersRepository usersRepository;
    private final DomainList domainList;
    private RecipientRewriteTableProcessor processor;

    @Inject
    public UsersRepositoryAliasingForwarding(UsersRepository usersRepository, DomainList domainList) {
        this.usersRepository = usersRepository;
        this.domainList = domainList;
    }

    @Override
    public void init() throws MessagingException {
        if (usersRepository instanceof RecipientRewriteTable) {
            RecipientRewriteTable virtualTableStore = (RecipientRewriteTable) usersRepository;
            processor = new RecipientRewriteTableProcessor(virtualTableStore, domainList, getMailetContext(), Mail.ERROR);
        } else {
            throw new MessagingException("The user repository is not RecipientRewriteTable");
        }
    }

    @Inject
    public final void setProcessor(RecipientRewriteTableProcessor processor) {
        this.processor = processor;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Preconditions.checkNotNull(mail);
        Preconditions.checkNotNull(mail.getMessage());
        Preconditions.checkNotNull(processor);

        processor.processMail(mail);
    }

    @Override
    public String getMailetInfo() {
        return "Local User Aliasing and Forwarding Mailet";
    }

}
