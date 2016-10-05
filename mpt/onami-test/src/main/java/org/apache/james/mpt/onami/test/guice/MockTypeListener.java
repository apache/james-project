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
import java.util.Map;

import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.reflection.AnnotationHandler;
import org.apache.james.mpt.onami.test.reflection.ClassVisitor;
import org.apache.james.mpt.onami.test.reflection.HandleException;

import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * <p>
 * {@link TypeListener} implementation.
 * </p>
 * <p>
 * Creates a specific {@link MockMembersInjector} for each {@link Mock} annotation found.
 * </p>
 *
 * @see MockMembersInjector
 * @see Mock
 */
public class MockTypeListener implements TypeListener {

    private final Map<Field, Object> mockedObjects;

    /**
     * Creates a new {@code MockTypeListener} instance given a map of mocked objects.
     *
     * @param mockedObjects a map of mocked objects
     */
    public MockTypeListener(Map<Field, Object> mockedObjects) {
        this.mockedObjects = mockedObjects;
    }

    /**
     * {@inheritDoc}
     */
    public <I> void hear(final TypeLiteral<I> typeLiteral, final TypeEncounter<I> typeEncounter) {
        try {
            new ClassVisitor()
                .registerHandler(Mock.class, new AnnotationHandler<Mock, Field>() {

                    public void handle(Mock annotation, Field field)
                        throws HandleException {
                        typeEncounter.register(new MockMembersInjector<I>(field, mockedObjects));
                    }

                })
                .visit(typeLiteral.getRawType());
        } catch (HandleException e) {
            typeEncounter.addError(e);
        }
    }

}
