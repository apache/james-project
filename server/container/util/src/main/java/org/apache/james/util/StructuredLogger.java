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

package org.apache.james.util;

import java.util.function.Consumer;

import org.slf4j.Logger;

public interface StructuredLogger {
    /**
     * Using Object::toString causes undesired formatting issues and might lead to complex formatting logic.
     * We migrated to explicit Strings instead.
     *
     * See https://issues.apache.org/jira/browse/JAMES-3587
     *
     * Use {@link StructuredLogger::field} instead.
     */
    @Deprecated
    StructuredLogger addField(String name, Object value);

    StructuredLogger field(String name, String value);

    void log(Consumer<Logger> logOperation);
}
