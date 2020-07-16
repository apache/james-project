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

package org.apache.james.imap.processor.fetch;

import java.io.IOException;

import org.apache.james.mailbox.model.Content;

public class HeadersBodyElement extends ContentBodyElement {

    private boolean noBody = false;
    
    public HeadersBodyElement(String name, Content content) {
        super(name, content);
    }


    /**
     * Indicate that there is no text body in the message. In this case we don't need to write a single CRLF in anycase if
     * this Element does not contain a header.
     */
    public void noBody() throws IOException {
        if (super.size() == 0) {
            noBody = true;
        }
    }
    
    @Override
    public long size() throws IOException {
        if (noBody) {
            return 0;
        } else {
            return super.size();

        }
    }

}
