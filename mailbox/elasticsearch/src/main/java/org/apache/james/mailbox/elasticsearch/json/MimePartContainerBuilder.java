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

package org.apache.james.mailbox.elasticsearch.json;

import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mime4j.stream.Field;

import java.io.InputStream;

public interface MimePartContainerBuilder {

    MimePart build();

    MimePartContainerBuilder using(TextExtractor textExtractor);

    MimePartContainerBuilder addToHeaders(Field field);

    MimePartContainerBuilder addBodyContent(InputStream bodyContent);

    MimePartContainerBuilder addChild(MimePart mimePart);

    MimePartContainerBuilder addFileName(String fileName);

    MimePartContainerBuilder addMediaType(String mediaType);

    MimePartContainerBuilder addSubType(String subType);

    MimePartContainerBuilder addContentDisposition(String contentDisposition);

}
