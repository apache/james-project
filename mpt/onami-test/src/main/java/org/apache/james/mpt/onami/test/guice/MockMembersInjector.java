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

package org.apache.james.mpt.onami.test.guice;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

import com.google.inject.MembersInjector;

/**
 * Class to inject via google-guice mock members into test cases classes.
 *
 * @param <T> type to inject members of
 */
public class MockMembersInjector<T> implements MembersInjector<T> {

    private final Field field;

    private final Map<Field, Object> mockedObjects;

    /**
     * Create a new instance.
     *
     * @param field         the field that has to be injected.
     * @param mockedObjects the map of mocked object.
     */
    public MockMembersInjector(final Field field, Map<Field, Object> mockedObjects) {
        this.field = field;
        this.mockedObjects = mockedObjects;
        AccessController.doPrivileged(new PrivilegedAction<Void>() {

            public Void run() {
                field.setAccessible(true);
                return null;
            }

        });
    }

    /**
     * {@inheritDoc}
     */
    public void injectMembers(T t) {
        try {
            field.set(t, mockedObjects.get(field));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
