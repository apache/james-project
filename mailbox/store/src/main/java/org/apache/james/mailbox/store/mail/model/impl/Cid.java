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

package org.apache.james.mailbox.store.mail.model.impl;


import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

public class Cid {

    public static Cid from(String cidAsString) {
        Preconditions.checkNotNull(cidAsString);
        Preconditions.checkArgument(!cidAsString.isEmpty(), "'cidAsString' is mandatory");
        return new Cid(normalizedCid(cidAsString));
    }

    private static String normalizedCid(String input) {
        if (isWrappedWithAngleBrackets(input)) {
            return unwrap(input);
        }
        return input;
    }
    
    private static String unwrap(String cidAsString) {
        return cidAsString.substring(1, cidAsString.length() - 1);
    }

    private static boolean isWrappedWithAngleBrackets(String cidAsString) {
        return cidAsString.startsWith("<") && cidAsString.endsWith(">");
    }

    private final String value;

    private Cid(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
    
    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof Cid) {
            Cid other = (Cid) obj;
            return Objects.equal(this.value, other.value);
        }
        return false;
    }
    
    @Override
    public final int hashCode() {
        return Objects.hashCode(this.value);
    }
}
