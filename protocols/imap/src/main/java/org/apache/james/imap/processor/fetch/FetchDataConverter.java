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
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MimePath;

class FetchDataConverter {

    static FetchGroup getFetchGroup(FetchData fetch) {
        FetchGroup result = FetchGroup.MINIMAL;

        if (fetch.isEnvelope()) {
            result = result.or(FetchGroup.HEADERS_MASK);
        }
        if (fetch.isBody() || fetch.isBodyStructure()) {
            result = result.or(FetchGroup.MIME_DESCRIPTOR_MASK);
        }

        Collection<BodyFetchElement> bodyElements = fetch.getBodyElements();
        if (bodyElements != null) {
            for (BodyFetchElement element : bodyElements) {
                final int sectionType = element.getSectionType();
                final int[] path = element.getPath();
                final boolean isBase = (path == null || path.length == 0);
                switch (sectionType) {
                    case BodyFetchElement.CONTENT:
                        if (isBase) {
                            result = addContent(result, path, isBase, FetchGroup.FULL_CONTENT_MASK);
                        } else {
                            result = addContent(result, path, isBase, FetchGroup.MIME_CONTENT_MASK);
                        }
                        break;
                    case BodyFetchElement.HEADER:
                    case BodyFetchElement.HEADER_NOT_FIELDS:
                    case BodyFetchElement.HEADER_FIELDS:
                        result = addContent(result, path, isBase, FetchGroup.HEADERS_MASK);
                        break;
                    case BodyFetchElement.MIME:
                        result = addContent(result, path, isBase, FetchGroup.MIME_HEADERS_MASK);
                        break;
                    case BodyFetchElement.TEXT:
                        result = addContent(result, path, isBase, FetchGroup.BODY_CONTENT_MASK);
                        break;
                    default:
                        break;
                }

            }
        }
        return result;
    }

    private static FetchGroup addContent(FetchGroup result, int[] path, boolean isBase, int content) {
        if (isBase) {
            return result.or(content);
        } else {
            MimePath mimePath = new MimePath(path);
            return result.addPartContent(mimePath, content);
        }
    }
}
