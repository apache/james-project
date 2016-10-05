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

import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.data.HelloWorld;
import org.apache.james.mpt.onami.test.data.SimpleModule;
import org.apache.james.mpt.onami.test.data.TelephonService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import com.google.inject.Injector;

@RunWith( OnamiRunner.class )
@GuiceModules( SimpleModule.class )
public class InjectMockObjectTestCase
    extends AbstractMockTestCase
{

    // Create and inject a simple EasyMock Strict mock
    @Mock
    private TelephonService telephonServiceMock;

    @Inject
    Injector injector;

    @Inject
    private HelloWorld helloWorld;

    @Test
    public void testMock()
    {
        EasyMock.expect( providedMock.go() ).andReturn( "Ciao" );
        EasyMock.replay( providedMock );

        Assert.assertNotNull( this.providedMock );
        Assert.assertEquals( "Ciao", helloWorld.sayHalloByService() );
        EasyMock.verify( providedMock );
    }

    @Test
    public void testMock2()
    {
        EasyMock.expect( providedMock.go() ).andReturn( "Ciao" );
        EasyMock.replay( providedMock );

        Assert.assertNotNull( this.providedMock );
        Assert.assertEquals( "Ciao", helloWorld.sayHalloByService() );
        EasyMock.verify( providedMock );
    }

    @Test
    public void testStrickMock()
    {
        EasyMock.expect( telephonServiceMock.getTelephonNumber() ).andReturn( "1234567890" );
        providedMock.call( "1234567890" );
        EasyMock.expectLastCall().once();
        EasyMock.replay( telephonServiceMock );
        EasyMock.replay( providedMock );

        helloWorld.callHelloWorldTelephon();

        EasyMock.verify( telephonServiceMock );
        EasyMock.verify( providedMock );

        // reset manually the mock object. Flag resettable is false!!!
        EasyMock.reset( telephonServiceMock );
    }

    @Test
    public void testStrickMock2()
    {
        Assert.assertNotNull( telephonServiceMock );
    }

}
