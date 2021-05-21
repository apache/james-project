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

package org.apache.james.mailets.flow;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

public class CollectingExecutionMailet extends GenericMailet {
    private static final ConcurrentLinkedDeque<MailAddress> executedFor = new ConcurrentLinkedDeque<>();

    public static void reset() {
        executedFor.clear();
    }

    public static List<MailAddress> executionFor() {
        return org.testcontainers.shaded.com.google.common.collect.ImmutableList.copyOf(executedFor);
    }

    @Override
    public void service(Mail mail) {
        executedFor.addAll(mail.getRecipients());
    }
}
