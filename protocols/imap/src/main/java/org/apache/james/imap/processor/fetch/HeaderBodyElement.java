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

/**
 * 
 */
package org.apache.james.imap.processor.fetch;

import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;

/**
 * {@link BodyElement} which represent a HEADER element specified by for example (BODY[1.HEADER])
 */
public class HeaderBodyElement extends MimeBodyElement {

    public HeaderBodyElement(String name, List<Header> headers) throws MailboxException {
        super(name, headers);
    }

    
    /**
     * Indicate that there is no text body in the message. In this case we don't need to write a single CRLF in anycase if
     * this Element does not contain a header.
     */
    public void noBody() {
        if (headers.isEmpty()) {
            size = 0;
        }
    }

    @Override
    protected long calculateSize(List<Header> headers) throws MailboxException {
        if (headers.isEmpty()) {
            // even if the headers are empty we need to include the headers body
            // seperator
            // See IMAP-294
            return ImapConstants.LINE_END.length();
        }
        return super.calculateSize(headers);
    }

}