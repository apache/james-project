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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class RootMimePartContainerBuilder implements MimePartContainerBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RootMimePartContainerBuilder.class);

    private MimePart rootMimePart;

    @Override
    public MimePart build() {
        return rootMimePart;
    }

    @Override public MimePartContainerBuilder using(TextExtractor textExtractor) {
        return this;
    }

    @Override
    public MimePartContainerBuilder addToHeaders(Field field) {
        LOGGER.warn("Trying to add headers to the Root MimePart container");
        return this;
    }

    @Override
    public MimePartContainerBuilder addBodyContent(InputStream bodyContent) {
        LOGGER.warn("Trying to add body content to the Root MimePart container");
        return this;
    }

    @Override
    public MimePartContainerBuilder addChild(MimePart mimePart) {
        if (rootMimePart == null) {
            rootMimePart = mimePart;
        } else {
            LOGGER.warn("Trying to add several children to the Root MimePart container");
        }
        return this;
    }

    @Override
    public MimePartContainerBuilder addFileName(String fileName) {
        LOGGER.warn("Trying to add fineName to the Root MimePart container");
        return this;
    }

    @Override
    public MimePartContainerBuilder addMediaType(String mediaType) {
        LOGGER.warn("Trying to add media type to the Root MimePart container");
        return this;
    }

    @Override
    public MimePartContainerBuilder addSubType(String subType) {
        LOGGER.warn("Trying to add sub type to the Root MimePart container");
        return this;
    }

    @Override
    public MimePartContainerBuilder addContentDisposition(String contentDisposition) {
        LOGGER.warn("Trying to add content disposition to the Root MimePart container");
        return this;
    }
}
