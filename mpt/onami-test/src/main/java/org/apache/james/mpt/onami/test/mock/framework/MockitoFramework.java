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

package org.apache.james.mpt.onami.test.mock.framework;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.james.mpt.onami.test.annotation.MockObjType.DEFAULT;

import org.apache.james.mpt.onami.test.annotation.MockObjType;
import org.apache.james.mpt.onami.test.mock.MockEngine;
import org.mockito.Mockito;

/**
 * Specifies the Mockito Framework.
 *
 * @see MockEngine
 */
public class MockitoFramework
    implements MockEngine
{

    /**
     * {@inheritDoc}
     */
    public void resetMock( Object... objects )
    {
        Mockito.reset( objects );
    }

    /**
     * {@inheritDoc}
     */
    public <T> T createMock( Class<T> cls, MockObjType type )
    {
        checkArgument( DEFAULT == type, "Unsupported mock type '%s' for Mockito Framework.", type );
        return Mockito.mock( cls );
    }

}
