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
package org.apache.james.imap.message.response;

import java.util.Objects;
import java.util.Set;

import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.api.message.response.ImapResponseMessage;

/**
 * A <code>CAPABILITY</code> response. See <code>7.2.1</code> of <a
 * href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt'
 * rel='tag'>RFC2060</a>.
 */
public class CapabilityResponse implements ImapResponseMessage {

    private final Set<Capability> capabilities;

    /**
     * Constructs a response based on the given capabilities.
     * 
     * @param capabilities
     *            not null
     */
    public CapabilityResponse(Set<Capability> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * Gets a {@link Set} containing the capabilities of this server.
     * 
     * @return not null
     */
    public Set<Capability> getCapabilities() {
        return capabilities;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof CapabilityResponse) {
            CapabilityResponse that = (CapabilityResponse) o;

            return Objects.equals(this.capabilities, that.capabilities);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(capabilities);
    }

    /**
     * Renders a description suitable for debugging.
     * 
     * @return a description suitable for debugging
     */
    public String toString() {
        return "CapabilityResponse ( " + "capabilities = " + this.capabilities + " )";
    }

}
