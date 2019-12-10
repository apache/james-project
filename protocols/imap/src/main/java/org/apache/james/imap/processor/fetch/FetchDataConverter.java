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

import java.util.Collection;

import org.apache.james.imap.api.message.BodyFetchElement;
import org.apache.james.imap.api.message.FetchData;
import org.apache.james.imap.api.message.FetchData.Item;
import org.apache.james.imap.api.message.SectionType;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MimePath;

class FetchDataConverter {

    static FetchGroup getFetchGroup(FetchData fetch) {
        FetchGroup result = FetchGroup.MINIMAL;

        if (fetch.contains(Item.ENVELOPE)) {
            result = result.with(FetchGroup.Profile.HEADERS);
        }
        if (fetch.contains(Item.BODY) || fetch.contains(Item.BODY_STRUCTURE)) {
            result = result.with(FetchGroup.Profile.MIME_DESCRIPTOR);
        }

        Collection<BodyFetchElement> bodyElements = fetch.getBodyElements();
        if (bodyElements != null) {
            for (BodyFetchElement element : bodyElements) {
                final SectionType sectionType = element.getSectionType();
                final int[] path = element.getPath();
                final boolean isBase = (path == null || path.length == 0);
                switch (sectionType) {
                    case CONTENT:
                        if (isBase) {
                            result = addContent(result, path, isBase, FetchGroup.Profile.FULL_CONTENT);
                        } else {
                            result = addContent(result, path, isBase, FetchGroup.Profile.MIME_CONTENT);
                        }
                        break;
                    case HEADER:
                    case HEADER_NOT_FIELDS:
                    case HEADER_FIELDS:
                        result = addContent(result, path, isBase, FetchGroup.Profile.HEADERS);
                        break;
                    case MIME:
                        result = addContent(result, path, isBase, FetchGroup.Profile.MIME_HEADERS);
                        break;
                    case TEXT:
                        result = addContent(result, path, isBase, FetchGroup.Profile.BODY_CONTENT);
                        break;
                    default:
                        break;
                }

            }
        }
        return result;
    }

    private static FetchGroup addContent(FetchGroup result, int[] path, boolean isBase, FetchGroup.Profile profile) {
        if (isBase) {
            return result.with(profile);
        } else {
            MimePath mimePath = new MimePath(path);
            return result.addPartContent(mimePath, profile);
        }
    }
}
