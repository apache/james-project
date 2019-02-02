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

package org.apache.james.mailet.experimental;

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

public class NonExperimentalMailet implements Mailet {

    @Override
    public void init(MailetConfig config) throws MessagingException {
    }

    @Override
    public void service(Mail mail) throws MessagingException {
    }

    @Override
    public void destroy() {
    }

    @Override
    public MailetConfig getMailetConfig() {
        return null;
    }

    @Override
    public String getMailetInfo() {
        return null;
    }

}
