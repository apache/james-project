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

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.data.HelloWorld;
import org.apache.james.mpt.onami.test.data.Service;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.AbstractModule;
import javax.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

@RunWith( OnamiRunner.class )
public class InjectDependingMockObjectTestCase
{

    @Mock
    static private Service service;

    @Inject
    Injector injector;

    private HelloWorld helloWorld;

    @Before
    public void setUp()
    {
        final List<Service> list = new ArrayList<Service>();
        list.add( service );

        AbstractModule listAbstractModule = new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( new TypeLiteral<List<Service>>()
                {
                } ).toInstance( list );
            }
        };

        Injector cInjector = injector.createChildInjector( listAbstractModule );
        helloWorld = cInjector.getInstance( HelloWorld.class );
        // required for optional dependencies
        cInjector.injectMembers( helloWorld );
    }

    @Test
    public void testMock()
    {
        Assert.assertNotNull( helloWorld );
        Assert.assertNotNull( service );
        EasyMock.expect( service.go() ).andReturn( "Ciao" );
        EasyMock.expectLastCall().once();

        EasyMock.replay( service );
        helloWorld.sayHalloByServiceLists();
        EasyMock.verify( service );
    }

}
