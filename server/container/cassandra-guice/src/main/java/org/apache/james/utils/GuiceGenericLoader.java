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

import com.google.inject.Injector;

public class GuiceGenericLoader<T> {

    private final Injector injector;
    private final String defaultPackageName;

    public GuiceGenericLoader(Injector injector, String defaultPackageName) {
        this.injector = injector;
        this.defaultPackageName = defaultPackageName;
    }

    public T instanciate(String className) throws Exception {
        Class<T> clazz = (Class<T>) ClassLoader.getSystemClassLoader().loadClass(constructFullName(className));
        return injector.getInstance(clazz);
    }

    private String constructFullName(String name) {
        if (! name.contains(".")) {
            return defaultPackageName + name;
        }
        return name;
    }

}
