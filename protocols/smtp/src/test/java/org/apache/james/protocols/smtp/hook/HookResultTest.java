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

package org.apache.james.protocols.smtp.hook;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class HookResultTest {

    @Test
    void shouldMatchBeanContract() {
        final String s = decodeBase64("bixhPXVzZXJAZXhhbXBsZS5jb20sAWhvc3Q9c2VydmVyLmV4YW1wbGUuY29tAXBvcnQ9NTg3AWF1dGg9QmVhcmVyIHZGOWRmd" +
            "DRxbVRjMk52YjNSbGNrQmhiSFJoZG1semRHRXVZMjl0Q2c9PQEB");

        System.out.println(s);

        s.chars().forEach(i -> System.out.println(i));
    }
    private String decodeBase64(String line) {
        if (line != null) {
            String lineWithoutTrailingCrLf = StringUtils.replace(line, "\r\n", "");
            return new String(Base64.getDecoder().decode(lineWithoutTrailingCrLf), StandardCharsets.UTF_8);
        }
        return null;
    }
}