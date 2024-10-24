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
import java.util.Collection;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.NamespaceResponse;
import org.apache.james.imap.message.response.NamespaceResponse.Namespace;

/**
 * Encodes namespace responses.
 */
public class NamespaceResponseEncoder implements ImapResponseEncoder<NamespaceResponse> {
    @Override
    public Class<NamespaceResponse> acceptableMessages() {
        return NamespaceResponse.class;
    }

    @Override
    public void encode(NamespaceResponse response, ImapResponseComposer composer) throws IOException {
        composer.untagged();
        composer.commandName(ImapConstants.NAMESPACE_COMMAND);

        encode(response.getPersonal(), composer);
        encode(response.getUsers(), composer);
        encode(response.getShared(), composer);

        composer.end();
    }

    private void encode(Collection<Namespace> namespaces, ImapResponseComposer composer) throws IOException {
        if (namespaces == null || namespaces.isEmpty()) {
            composer.nil();
        } else {
            composer.openParen();
            for (NamespaceResponse.Namespace namespace : namespaces) {
                encode(namespace, composer);
            }
            composer.closeParen();
        }
    }

    private void encode(Namespace namespace, ImapResponseComposer composer) throws IOException {
        composer.openParen();
        String prefix = namespace.getPrefix();
        String delimiter = Character.toString(namespace.getDelimiter());

        if (!prefix.isEmpty()) {
            prefix = prefix + delimiter;
        }
        composer.quote(prefix);
        composer.quote(delimiter);
        composer.closeParen();
    }
}
