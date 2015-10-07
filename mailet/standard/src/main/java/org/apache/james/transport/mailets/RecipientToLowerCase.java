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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.base.GenericMailet;

/**
 * {@link GenericMailet} which convert all Recipients to lowercase
 * 
 *
 */
public class RecipientToLowerCase extends GenericMailet{

    @Override
    public void service(Mail mail) throws MessagingException {
        Iterator<MailAddress> rcpts = mail.getRecipients().iterator();
        List<MailAddress> newRcpts = new ArrayList<MailAddress>();
        while(rcpts.hasNext()) {
            newRcpts.add(new MailAddress(rcpts.next().toString().toLowerCase(Locale.US)));
        }
        mail.setRecipients(newRcpts);
    }

}
