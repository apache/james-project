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
import java.util.ArrayList;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.process.MailboxType;
import org.apache.james.imap.message.response.AbstractListingResponse;

/**
 * Utilities for encoding LIST and LSUB responses.
 */
public class ListingEncodingUtils {

    public static void encodeListingResponse(String responseTypeName, ImapResponseComposer composer, AbstractListingResponse response) throws IOException {
        final List<String> attributes = getNameAttributes(response);

        final String name = response.getName();
        final char hierarchyDelimiter = response.getHierarchyDelimiter();
                
        composer.untagged();
        composer.message(responseTypeName);
        composer.openParen();
        if (attributes != null) {
            for (String attribute : attributes) {
                composer.message(attribute);
            }
        }
        composer.closeParen();

        if (hierarchyDelimiter == Character.UNASSIGNED) {
        	composer.nil();
        } else {
        	composer.quote(Character.toString(hierarchyDelimiter));
        }
        composer.mailbox(name);

        composer.end();
    }

    private static List<String> getNameAttributes(AbstractListingResponse response) {
        final List<String> attributes;
        if (response.isNameAttributed()) {
            attributes = new ArrayList<>();
            if (response.isNoInferiors()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_NOINFERIORS);
            }
            if (response.isNoSelect()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_NOSELECT);
            }
            if (response.isMarked()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_MARKED);
            }
            if (response.isUnmarked()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_UNMARKED);
            }
            if (response.hasChildren()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_HAS_CHILDREN);
            }
            if (response.hasNoChildren()) {
                attributes.add(ImapConstants.NAME_ATTRIBUTE_HAS_NO_CHILDREN);
            }
            if (!MailboxType.OTHER.equals(response.getType())) {
                attributes.add(response.getType().getAttributeName());
            }
        } else {
            attributes = null;
        }
        return attributes;
    }

}
