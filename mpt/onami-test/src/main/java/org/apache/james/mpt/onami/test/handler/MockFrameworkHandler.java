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

package org.apache.james.mpt.onami.test.handler;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.MockFramework;
import org.apache.james.mpt.onami.test.annotation.MockType;
import org.apache.james.mpt.onami.test.reflection.ClassHandler;
import org.apache.james.mpt.onami.test.reflection.HandleException;

/**
 * Handler class to handle all {@link MockFramework} annotations.
 *
 * @see org.apache.onami.test.reflection.ClassVisitor
 * @see MockFramework
 */
public final class MockFrameworkHandler implements ClassHandler<MockFramework> {

    private static final Logger LOGGER = Logger.getLogger(MockFrameworkHandler.class.getName());

    private MockType mockType;

    /**
     * @return the mockType
     */
    public MockType getMockType() {
        return mockType;
    }

    /**
     * {@inheritDoc}
     */
    public void handle(MockFramework annotation, Class<?> element)
        throws HandleException {
        if (mockType != null && mockType != annotation.value()) {
            throw new HandleException("Inconsistent mock framework found. " + "Mock framework already set [set: "
                + mockType + " now found: " + annotation.value() + "]");
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.finer("  Found MockFramework: " + annotation.value());
        }

        mockType = annotation.value();
    }

}
