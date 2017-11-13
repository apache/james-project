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

package org.apache.james.jmap.send;

import java.io.IOException;

import javax.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.james.jmap.model.Envelope;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.james.server.core.MailImpl;
import org.apache.mailet.Mail;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

public class MailFactory {
    
    @VisibleForTesting MailFactory() {
    }

    public Mail build(MetaDataWithContent message, Envelope envelope) throws MessagingException, IOException {
        ImmutableSet<MailAddress> recipients = Sets.union(
            Sets.union(envelope.getTo(), envelope.getCc()),
                envelope.getBcc()).immutableCopy();
        return new MailImpl(message.getMessageId().serialize(),
            envelope.getFrom(), recipients, message.getContent());
    }

}
