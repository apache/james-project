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

package org.apache.james.rrt.api;

import org.apache.james.rrt.lib.Mapping;
import org.apache.james.rrt.lib.MappingSource;

public class LoopDetectedException extends RecipientRewriteTableException {
    public LoopDetectedException(MappingSource source, Mapping mapping) {
        super(String.format("Creation of redirection of %s to %s would lead to a loop, operation not performed",
            source.asString(),
            mapping.asString()));
    }
}
