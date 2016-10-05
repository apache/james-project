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

import org.apache.james.mpt.onami.test.OnamiRunner;
import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.data.HelloWorld;
import org.apache.james.mpt.onami.test.data.Service;
import org.apache.james.mpt.onami.test.data.ServiceModule;
import org.apache.james.mpt.onami.test.data.TelephonService;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

@RunWith( OnamiRunner.class )
@GuiceModules( ServiceModule.class )
public class TestCustomInjectionTest
{

    @Mock
    private static Service service;

    @Inject
    private TelephonService telephonService;

    @Inject
    private HelloWorld helloWorld;

    @BeforeClass
    public static void setUp()
    {
        Assert.assertNotNull( service );
        // service.go();
    }

    @Test
    public void test()
        throws Exception
    {
        Assert.assertNotNull( service );
        Assert.assertNotNull( telephonService );
        Assert.assertNotNull( helloWorld );
    }

    @Test
    public void testOverideModule()
        throws Exception
    {
        Assert.assertNotNull( service );
        Assert.assertNotNull( telephonService );
        Assert.assertEquals( "It's real class", telephonService.getTelephonNumber() );

        EasyMock.expect( service.go() ).andReturn( "Mocked injected class" );
        EasyMock.replay( service );

        Assert.assertEquals( "Mocked injected class", helloWorld.sayHalloByService() );
        EasyMock.verify( service );
    }

}
