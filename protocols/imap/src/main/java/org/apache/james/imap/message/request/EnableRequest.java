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
package org.apache.james.imap.message.request;

import java.util.List;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.Capability;

public class EnableRequest extends AbstractImapRequest {

    private final List<Capability> capabilities;

    public EnableRequest(Tag tag, ImapCommand command, List<Capability> capabilities) {
        super(tag, command);
        this.capabilities = capabilities;
    }
    
    /**
     * Return a List of <code>CAPABILITIES</code>. All these must be uppercase
     * 
     * @return caps
     */
    public List<Capability> getCapabilities() {
        return capabilities;
    }
}
