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
package org.apache.james.webadmin.jackson;

import org.apache.james.mailbox.quota.QuotaCount;
import org.apache.james.mailbox.quota.QuotaSize;
import org.apache.james.webadmin.dto.QuotaValueDeserializer;
import org.apache.james.webadmin.dto.QuotaValueSerializer;
import org.apache.james.webadmin.utils.JsonTransformerModule;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class QuotaModule implements JsonTransformerModule {

    private final SimpleModule simpleModule;

    public QuotaModule() {
        simpleModule = new SimpleModule()
            .addSerializer(QuotaSize.class, new QuotaValueSerializer<>())
            .addSerializer(QuotaCount.class, new QuotaValueSerializer<>())
            .addDeserializer(QuotaSize.class, new QuotaValueDeserializer<>(QuotaSize.unlimited(), QuotaSize::size))
            .addDeserializer(QuotaCount.class, new QuotaValueDeserializer<>(QuotaCount.unlimited(), QuotaCount::count));
    }

    @Override
    public Module asJacksonModule() {
        return simpleModule;
    }
}
