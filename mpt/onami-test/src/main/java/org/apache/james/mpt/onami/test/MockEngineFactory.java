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

package org.apache.james.mpt.onami.test;

import org.apache.james.mpt.onami.test.annotation.MockType;
import org.apache.james.mpt.onami.test.mock.MockEngine;
import org.apache.james.mpt.onami.test.mock.framework.EasyMockFramework;
import org.apache.james.mpt.onami.test.mock.framework.MockitoFramework;

/**
 * Factory class to create the mock framework.
 * 
 * @see org.apache.onami.test.annotation.MockFramework
 */
final class MockEngineFactory
{

    /**
     * Hidden constructor, this class must not be instantiated directly.
     */
    private MockEngineFactory()
    {
        // do nothing
    }

    /**
     * Mock factory constructor. <br>
     * Supported framewors: <li> {@link MockType}.EASY_MOCK <li> {@link MockType}.MOCKITO <br>
     *
     * @see MockType
     * @param type of mock framework to create.
     * @return An instance of mock framework.
     */
    public static MockEngine getMockEngine(MockType type )
    {
        switch ( type )
        {
            case EASY_MOCK:
                return new EasyMockFramework();

            case MOCKITO:
                return new MockitoFramework();

            default:
                throw new IllegalArgumentException( "Unrecognized MockType '" + type.name() + "'" );
        }
    }

}
