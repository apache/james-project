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
package org.apache.james.mailbox.store.search.comparator;

import java.util.Comparator;

import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.SearchUtil;

public class HeaderMailboxComparator extends AbstractHeaderComparator {
    public static final Comparator<MailboxMessage> FROM_COMPARATOR = new HeaderMailboxComparator(FROM);
    public static final Comparator<MailboxMessage> TO_COMPARATOR = new HeaderMailboxComparator(TO);
    public static final Comparator<MailboxMessage> CC_COMPARATOR = new HeaderMailboxComparator(CC);

    private final String headerName;

    public HeaderMailboxComparator(String headerName) {
        this.headerName = headerName;
    }

    @Override
    public int compare(MailboxMessage o1, MailboxMessage o2) {
        String mailbox1 = SearchUtil.getMailboxAddress(getHeaderValue(headerName, o1));
        String mailbox2 = SearchUtil.getMailboxAddress(getHeaderValue(headerName, o2));

        return mailbox1.compareToIgnoreCase(mailbox2);
    }
}

