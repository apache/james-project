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

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult.Header;
import org.apache.james.mailbox.store.ResultUtils;
import org.apache.james.mailbox.store.mail.model.Message;


public abstract class AbstractHeaderComparator implements Comparator<Message<?>>{

    public final static String FROM ="from";
    public final static String TO ="to";
    public final static String CC ="cc";

    protected String getHeaderValue(String headerName, Message<?> message) {
        try {
            final List<Header> headers = ResultUtils.createHeaders(message);
            for (Header header : headers) {
                try {
                    String name = header.getName();
                    if (headerName.equalsIgnoreCase(name)) {
                        final String value = header.getValue();
                        return value.toUpperCase(Locale.ENGLISH);
                    }
                } catch (MailboxException e) {
                    // skip the header line
                }

            }
        } catch (IOException e) {
            // skip the header
        }
        return "";
    }
}
