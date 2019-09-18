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

package org.apache.james.jmap.draft.model;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

public class MessagePreviewGenerator {
    
    public static final String NO_BODY = "(Empty)";
    public static final int MAX_PREVIEW_LENGTH = 256;

    public String compute(Optional<String> textBody) {
        return textBody.map(StringUtils::normalizeSpace)
            .filter(text -> !text.isEmpty())
            .map(this::truncateToMaxLength)
            .orElse(NO_BODY);
    }

    private String truncateToMaxLength(String body) {
        return StringUtils.left(body, MAX_PREVIEW_LENGTH);
    }
}
