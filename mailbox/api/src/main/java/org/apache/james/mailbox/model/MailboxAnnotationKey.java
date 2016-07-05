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

package org.apache.james.mailbox.model;

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import org.apache.commons.lang.StringUtils;

/**
 * Created by quynh on 04/07/2016.
 */
public class MailboxAnnotationKey {
    public static final String SLASH_CHARACTER = "/";

    public static final String TWO_SLASH_CHARACTER = "//";

    private static final CharMatcher NAME_ANNOTATION_PATTERN = CharMatcher.ASCII
        .and(CharMatcher.inRange('\u0000', '\u0019').negate()).and(CharMatcher.isNot('*'))
        .and(CharMatcher.isNot('%'));

    private final String key;

    public MailboxAnnotationKey(String key) {
        this.key = key;
    }

    public boolean isValid() {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        if (key.contains(" ")) {
            return false;
        }
        if (!key.startsWith(SLASH_CHARACTER)) {
            return false;
        }
        if (key.contains(TWO_SLASH_CHARACTER)) {
            return false;
        }
        if (key.endsWith(SLASH_CHARACTER)) {
            return false;
        }
        if (!NAME_ANNOTATION_PATTERN.matchesAllOf(key)) {
            return false;
        }
        return true;
    }

    public int countSlash() {
        return StringUtils.countMatches(key, SLASH_CHARACTER);
    }

    public String getKey() {
        return key;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxAnnotationKey) {
            MailboxAnnotationKey anotherKey = (MailboxAnnotationKey)obj;
            return Objects.equal(anotherKey.getKey(), key);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }
}