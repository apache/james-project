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

package org.apache.james.dto;

import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;

public class TestModule<T extends BaseType, U extends DTO> extends DTOModule<T, U> {

    protected TestModule(DTOConverter<T, U> converter, DomainObjectConverter<T, U> toDomainObjectConverter, Class<T> domainObjectType, Class<U> dtoType, String typeName) {
        super(converter, toDomainObjectConverter, domainObjectType, dtoType, typeName);
    }
}
