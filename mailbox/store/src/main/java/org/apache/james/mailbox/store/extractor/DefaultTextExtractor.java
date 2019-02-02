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

package org.apache.james.mailbox.store.extractor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;

/**
 * A default text extractor that is directly based on the input file provided.
 * 
 * Costs less calculations that TikaTextExtractor, but result is not that good.
 */
public class DefaultTextExtractor implements TextExtractor {

    @Override
    public ParsedContent extractContent(InputStream inputStream, String contentType) throws Exception {
        if (contentType != null && contentType.startsWith("text/")) {
            return new ParsedContent(Optional.ofNullable(IOUtils.toString(inputStream, StandardCharsets.UTF_8)), new HashMap<>());
        } else {
            return new ParsedContent(Optional.empty(), new HashMap<>());
        }
    }
}
