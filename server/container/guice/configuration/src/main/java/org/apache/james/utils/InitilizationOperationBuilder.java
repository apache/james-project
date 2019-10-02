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

import org.apache.james.lifecycle.api.Startable;

public class InitilizationOperationBuilder {

    @FunctionalInterface
    public interface Init {
        void init() throws Exception;
    }

    @FunctionalInterface
    public interface RequireInit {
        InitializationOperation init(Init init);
    }

    public static RequireInit forClass(Class<? extends Startable> type) {
        return init -> new PrivateImpl(init, type);
    }

    private static class PrivateImpl implements InitializationOperation {

        private final Init init;
        private final Class<? extends Startable> type;

        private PrivateImpl(Init init, Class<? extends Startable> type) {
            this.init = init;
            this.type = type;
        }

        @Override
        public void initModule() throws Exception {
            init.init();
        }

        @Override
        public Class<? extends Startable> forClass() {
            return type;
        }
    }
}
