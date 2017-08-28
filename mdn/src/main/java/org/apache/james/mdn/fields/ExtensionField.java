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

/**
 * Implements extension fields allowed by RFC-8098
 *
 * https://tools.ietf.org/html/rfc8098#section-3.3
 */
public class ExtensionField implements Field {
    private final String fieldName;
    private final String rawValue;

    public ExtensionField(String fieldName, String rawValue) {
        this.fieldName = fieldName;
        this.rawValue = rawValue;
    }

    @Override
    public String formattedValue() {
        return fieldName + ": " +rawValue;
    }
}
