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

package org.apache.james.mdn.fields;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;

public class Text {

    public static Text fromRawText(String rawText) {
        Preconditions.checkNotNull(rawText);
        String trimmedText = rawText.trim();
        Preconditions.checkArgument(!trimmedText.isEmpty(), "Text should not be empty");

        return new Text(replaceLineBreaksByContinuation(trimmedText));
    }

    private static String replaceLineBreaksByContinuation(String rawText) {
        return Joiner.on("\r\n ")
            .join(Splitter.on("\n")
                .trimResults()
                .splitToList(rawText));
    }

    private final String content;

    public Text(String content) {
        this.content = content;
    }

    public String formatted() {
        return content;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Text) {
            Text that = (Text) o;

            return Objects.equal(this.content, that.content);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(content);
    }
}
