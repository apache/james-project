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

package org.apache.james.mailbox.tika;

import java.io.InputStream;

import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MimeType;

import com.google.common.collect.ImmutableSet;

public class ContentTypeFilteringTextExtractor implements TextExtractor {

    private final TextExtractor textExtractor;
    private final ImmutableSet<MimeType> contentTypeBlacklist;

    public ContentTypeFilteringTextExtractor(TextExtractor textExtractor, ImmutableSet<MimeType> contentTypeBlacklist) {
        this.textExtractor = textExtractor;
        this.contentTypeBlacklist = contentTypeBlacklist;
    }

    @Override
    public ParsedContent extractContent(InputStream inputStream, ContentType contentType) throws Exception {
        if (isBlacklisted(contentType.mimeType())) {
            return ParsedContent.empty();
        }
        return textExtractor.extractContent(inputStream, contentType);
    }

    private boolean isBlacklisted(MimeType contentType) {
        return contentTypeBlacklist.contains(contentType);
    }

}
