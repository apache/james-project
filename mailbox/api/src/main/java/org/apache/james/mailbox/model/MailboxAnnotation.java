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

import org.apache.commons.lang.StringUtils;

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

public class MailboxAnnotation {

    private static final CharMatcher NAME_ANNOTATION_PATTERN = CharMatcher.ASCII
            .and(CharMatcher.inRange('\u0000', '\u0019').negate()).and(CharMatcher.isNot('*'))
            .and(CharMatcher.isNot('%'));
    
    public static final String SLASH_CHARACTER = "/";
    
    public static final String TWO_SLASH_CHARACTER = "//";
    
    public static MailboxAnnotation nil(String key) {
        return new MailboxAnnotation(key, Optional.<String> absent());
    }

    public static MailboxAnnotation newInstance(String key, String value) {
        return new MailboxAnnotation(key, Optional.of(value));
    }

    private final String key;
    private final Optional<String> value;

    private MailboxAnnotation(String key, Optional<String> value) {
        Preconditions.checkNotNull(key);
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(isValidKey(key), 
                "Key must start with '/' and not end with '/' and does not contain charater with hex from '\u0000' to '\u00019' or {'*', '%', two consecutive '/'} ");
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public Optional<String> getValue() {
        return value;
    }

    private static boolean isValidKey(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        String key = input.trim();
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

    @Override
    public int hashCode() {
        return Objects.hashCode(key, value);
    }

    public boolean isNil() {
        return !value.isPresent();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MailboxAnnotation) {
            MailboxAnnotation o = (MailboxAnnotation) obj;
            return Objects.equal(key, o.getKey()) && Objects.equal(value, o.getValue());
        } else {
            return false;
        }
    }
}
