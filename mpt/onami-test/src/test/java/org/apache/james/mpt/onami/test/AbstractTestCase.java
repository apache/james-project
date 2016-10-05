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

import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.onami.test.annotation.GuiceProvidedModules;
import org.apache.james.mpt.onami.test.data.SimpleModule;
import org.junit.runner.RunWith;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.name.Names;

@RunWith( OnamiRunner.class )
@GuiceModules( SimpleModule.class )
abstract public class AbstractTestCase
    extends AbstractEmptyTestCase
{

    @GuiceProvidedModules
    public static Module genericModule()
    {
        return new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( String.class ).annotatedWith( Names.named( "test.info.inject" ) ).toInstance( "JUnice = JUnit + Guice" );
            }
        };
    }

    @GuiceProvidedModules
    public static Iterable<Module> genericModule2()
    {
        AbstractModule a = new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( String.class ).annotatedWith( Names.named( "test.info.inject2" ) ).toInstance( "JUnice = JUnit + Guice Iterable" );
            }
        };

        ArrayList<Module> al = new ArrayList<Module>();
        al.add( a );
        return al;
    }

    @GuiceProvidedModules
    public static Module[] genericModule3()
    {
        AbstractModule a = new AbstractModule()
        {
            @Override
            protected void configure()
            {
                bind( String.class ).annotatedWith( Names.named( "test.info.inject3" ) ).toInstance( "JUnice = JUnit + Guice Array" );
            }
        };
        return new Module[] { a };
    }

}
