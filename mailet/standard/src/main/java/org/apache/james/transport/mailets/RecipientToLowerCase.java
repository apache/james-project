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

import java.util.Locale;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;

/**
 * {@link GenericMailet} which convert all Recipients to lowercase
 * 
 *
 */
public class RecipientToLowerCase extends GenericMailet{

    @Override
    public void service(Mail mail) throws MessagingException {
        mail.setRecipients(FluentIterable.from(mail.getRecipients())
            .transform(this::toLowerCase)
            .toList());
    }

    private MailAddress toLowerCase(MailAddress mailAddress) {
        try {
            return new MailAddress(mailAddress.asString().toLowerCase(Locale.US));
        } catch (AddressException e) {
            throw Throwables.propagate(e);
        }
    }

}
