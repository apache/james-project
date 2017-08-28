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
 * MDN-Gateway field as specified in https://tools.ietf.org/html/rfc8098#section-3.2.2
 */
public class Gateway implements Field {
    private static final String DNS = "dns";
    public static final String FIELD_NAME = "MDN-Gateway";

    private final String nameType;
    private final Text name;

    public Gateway(String nameType, Text name) {
        this.nameType = nameType;
        this.name = name;
    }

    public Gateway(Text name) {
        this(DNS, name);
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + nameType + ";" + name.formatted();
    }

    public String getNameType() {
        return nameType;
    }

    public Text getName() {
        return name;
    }
}
