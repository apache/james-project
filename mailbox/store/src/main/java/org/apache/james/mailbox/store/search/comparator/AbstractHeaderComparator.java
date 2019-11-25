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

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class AbstractHeaderComparator implements Comparator<MailboxMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractHeaderComparator.class);

    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String CC = "cc";

    protected String getHeaderValue(String headerName, MailboxMessage message) {
        try {
            final List<Header> headers = ResultUtils.createHeaders(message);
            for (Header header : headers) {
                String name = header.getName();
                if (headerName.equalsIgnoreCase(name)) {
                    final String value = header.getValue();
                    return value.toUpperCase(Locale.US);
                }

            }
        } catch (IOException e) {
            LOGGER.warn("Exception encountered, skipping header line", e);
            // skip the header
        }
        return "";
    }
}
