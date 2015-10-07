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

import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.util.ByteSequence;

import java.util.Objects;

public class FieldImpl implements Field {
    private final String name;
    private final String body;

    public FieldImpl(String name, String body) {
        this.name = name;
        this.body = body;
    }

    public String getName() {
        return name;
    }

    public String getBody() {
        return body;
    }

    public ByteSequence getRaw() {
        return null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, body);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof  FieldImpl) {
            FieldImpl otherField = (FieldImpl) o;
            return Objects.equals(name, otherField.name)
                && Objects.equals(body, otherField.body);
        }
        return false;
    }
}
