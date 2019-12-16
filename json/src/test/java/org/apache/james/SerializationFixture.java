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

package org.apache.james;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.dto.BaseType;
import org.apache.james.dto.FirstDomainObject;
import org.apache.james.dto.FirstNestedType;
import org.apache.james.dto.NestedType;
import org.apache.james.dto.SecondDomainObject;
import org.apache.james.dto.SecondNestedType;

public interface SerializationFixture {
    Optional<NestedType> NO_CHILD = Optional.empty();
    FirstDomainObject FIRST = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "first payload", NO_CHILD);
    BaseType SECOND = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload", NO_CHILD);
    BaseType SECOND_WITH_NESTED = new SecondDomainObject(UUID.fromString("4a2c853f-7ffc-4ce3-9410-a47e85b3b741"), "second payload", Optional.of(new FirstNestedType(12)));
    BaseType FIRST_WITH_NESTED = new FirstDomainObject(Optional.of(1L), ZonedDateTime.parse("2016-04-03T02:01+07:00[Asia/Vientiane]"), "payload", Optional.of(new SecondNestedType("bar")));

    String MISSING_TYPE_JSON = "{\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String DUPLICATE_TYPE_JSON = "{\"type\":\"first\", \"type\":\"second\", \"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String FIRST_JSON = "{\"type\":\"first\",\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String FIRST_JSON_BAD = "{\"type\":\"first\",\"id\":2,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"first payload\"}";
    String FIRST_JSON_WITH_NESTED = "{\"type\":\"first\",\"id\":1,\"time\":\"2016-04-03T02:01+07:00[Asia/Vientiane]\",\"payload\":\"payload\", \"child\": {\"bar\": \"bar\", \"type\": \"second-nested\"}}";
    String SECOND_JSON = "{\"type\":\"second\",\"id\":\"4a2c853f-7ffc-4ce3-9410-a47e85b3b741\",\"payload\":\"second payload\"}";
    String SECOND_WITH_NESTED_JSON = "{\"type\":\"second\",\"id\":\"4a2c853f-7ffc-4ce3-9410-a47e85b3b741\",\"payload\":\"second payload\", \"child\": {\"foo\": 12, \"type\": \"first-nested\"}}";
}
