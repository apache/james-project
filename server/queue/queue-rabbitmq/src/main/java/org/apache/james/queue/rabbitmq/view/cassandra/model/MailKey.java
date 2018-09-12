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

package org.apache.james.queue.rabbitmq.view.cassandra.model;

import org.apache.mailet.Mail;

import com.google.common.base.Preconditions;

public class MailKey {

    public static MailKey fromMail(Mail mail) {
        return of(mail.getName());
    }

    public static MailKey of(String mailKey) {
        return new MailKey(mailKey);
    }

    private final String mailKey;

    private MailKey(String mailKey) {
        Preconditions.checkNotNull(mailKey);
        Preconditions.checkArgument(!mailKey.isEmpty());

        this.mailKey = mailKey;
    }

    public String getMailKey() {
        return mailKey;
    }
}
