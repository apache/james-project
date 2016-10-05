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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.GuiceModules;
import org.apache.james.mpt.onami.test.reflection.ClassHandler;
import org.apache.james.mpt.onami.test.reflection.HandleException;

import com.google.inject.Module;

/**
 * Handler class to handle all {@link GuiceModules} annotations.
 *
 * @see org.apache.onami.test.reflection.ClassVisitor
 */
public final class GuiceModuleHandler
    implements ClassHandler<GuiceModules>
{

    private static final Logger LOGGER = Logger.getLogger( GuiceModuleHandler.class.getName() );

    private final List<Module> modules = new ArrayList<Module>();

    /**
     * @return the modules
     */
    public List<Module> getModules()
    {
        return modules;
    }

    /**
     * {@inheritDoc}
     */
    public void handle( GuiceModules annotation, Class<?> element )
        throws HandleException
    {
        for ( Class<? extends Module> module : annotation.value() )
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "   Try to create module: " + module );
            }
            try
            {
                modules.add( module.newInstance() );
            }
            catch ( Exception e )
            {
                throw new HandleException( e );
            }
        }
    }

}
