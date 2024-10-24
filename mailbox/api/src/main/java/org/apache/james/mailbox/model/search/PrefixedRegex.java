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

package org.apache.james.mailbox.model.search;

import java.util.Objects;
import java.util.Optional;
import java.util.StringTokenizer;

import com.google.re2j.Pattern;

public class PrefixedRegex implements MailboxNameExpression {

    private final String prefix;
    private final String regex;
    private final Pattern pattern;
    private final char pathDelimiter;

    public PrefixedRegex(String prefix, String regex, char pathDelimiter) {
        this.prefix = Optional.ofNullable(prefix).orElse("");
        this.regex = Optional.ofNullable(regex).orElse("");
        this.pathDelimiter = pathDelimiter;
        this.pattern = constructEscapedRegex(this.regex);
    }

    @Override
    public boolean isExpressionMatch(String name) {
        if (name.startsWith(prefix)) {
            String nameSubstring = sanitizeSubstring(name.substring(prefix.length()));
            return regexMatching(nameSubstring);
        }
        return false;
    }

    private String sanitizeSubstring(String name) {
        if (!name.isEmpty() && name.charAt(0) == pathDelimiter) {
            return name.substring(1);
        }
        return name;
    }

    private boolean regexMatching(String name) {
        if (isWild()) {
            return name != null
                && pattern.matcher(name).matches();
        } else {
            return regex.equals(name);
        }
    }

    @Override
    public MailboxNameExpression includeChildren() {
        return new PrefixedRegex(prefix, regex + "*", pathDelimiter);
    }

    @Override
    public String getCombinedName() {
        if (prefix != null && prefix.length() > 0) {
            final int baseLength = prefix.length();
            if (prefix.charAt(baseLength - 1) == pathDelimiter) {
                if (regex != null && regex.length() > 0) {
                    if (regex.charAt(0) == pathDelimiter) {
                        return prefix + regex.substring(1);
                    } else {
                        return prefix + regex;
                    }
                } else {
                    return prefix;
                }
            } else {
                if (regex != null && regex.length() > 0) {
                    if (regex.charAt(0) == pathDelimiter) {
                        return prefix + regex;
                    } else {
                        return prefix + pathDelimiter + regex;
                    }
                } else {
                    return prefix;
                }
            }
        } else {
            return regex;
        }
    }

    @Override
    public boolean isWild() {
        return regex != null
            && (
            regex.indexOf(FREEWILDCARD) >= 0
                || regex.indexOf(LOCALWILDCARD) >= 0);
    }

    private Pattern constructEscapedRegex(String regex) {
        StringBuilder stringBuilder = new StringBuilder();
        StringTokenizer tokenizer = new StringTokenizer(regex, "*%", true);
        while (tokenizer.hasMoreTokens()) {
            stringBuilder.append(getRegexPartAssociatedWithToken(tokenizer));
        }
        return Pattern.compile(stringBuilder.toString());
    }

    private String getRegexPartAssociatedWithToken(StringTokenizer tokenizer) {
        String token = tokenizer.nextToken();
        if (token.equals("*")) {
            return ".*";
        } else if (token.equals("%")) {
            return "[^" + Pattern.quote(String.valueOf(pathDelimiter)) + "]*";
        } else {
            return Pattern.quote(token);
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PrefixedRegex) {
            PrefixedRegex that = (PrefixedRegex) o;

            return Objects.equals(this.pathDelimiter, that.pathDelimiter)
                && Objects.equals(this.prefix, that.prefix)
                && Objects.equals(this.regex, that.regex);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(prefix, regex, pathDelimiter);
    }

    @Override
    public String toString() {
        return "PrefixedRegex{" +
            "prefix='" + prefix + '\'' +
            ", regex='" + regex + '\'' +
            '}';
    }
}
