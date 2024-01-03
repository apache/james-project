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

package org.apache.james.utils;

import java.util.List;

import org.apache.james.lifecycle.api.Startable;

import com.google.common.collect.ImmutableList;

public interface InitializationOperation {

    int DEFAULT_PRIORITY = 0;

    void initModule() throws Exception;

    /**
     * In order to initialize components in the right order, every
     * {@link InitializationOperation} is supposed to declare which
     * class it will initialize.
     *
     * @return the Class that this object will initialize.
     */
    Class<? extends Startable> forClass();

    default List<Class<?>> requires() {
        return ImmutableList.of();
    }

    default int priority() {
        return DEFAULT_PRIORITY;
    }

}
