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
package org.apache.james.imap.encode;

import java.io.IOException;
import java.util.Iterator;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.message.response.CapabilityResponse;

/**
 * Encodes <code>CAPABILITY</code> response. See <code>7.2.1</code> of <a
 * href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt'
 * rel='tag'>RFC2060</a>.
 */
public class CapabilityResponseEncoder implements ImapResponseEncoder<CapabilityResponse> {
    @Override
    public Class<CapabilityResponse> acceptableMessages() {
        return CapabilityResponse.class;
    }

    @Override
    public void encode(CapabilityResponse response, ImapResponseComposer composer) throws IOException {
        Iterator<Capability> capabilities = response.getCapabilities().iterator();
        composer.untagged();
        composer.message(ImapConstants.CAPABILITY_COMMAND_NAME);
        while (capabilities.hasNext()) {
            composer.message(capabilities.next().asString());
        }
        composer.end();        
    }
}
