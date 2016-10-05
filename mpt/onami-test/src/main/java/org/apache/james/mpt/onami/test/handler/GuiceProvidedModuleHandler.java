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

import static java.lang.String.format;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.GuiceProvidedModules;
import org.apache.james.mpt.onami.test.reflection.HandleException;
import org.apache.james.mpt.onami.test.reflection.MethodHandler;

import com.google.inject.Module;
import com.google.inject.TypeLiteral;

/**
 * Handler class to handle all {@link GuiceProvidedModules} annotations.
 *
 * @see org.apache.onami.test.reflection.ClassVisitor
 * @see GuiceProvidedModules
 */
public final class GuiceProvidedModuleHandler
    implements MethodHandler<GuiceProvidedModules>
{

    private static final Logger LOGGER = Logger.getLogger( GuiceProvidedModuleHandler.class.getName() );

    private final List<Module> modules = new ArrayList<Module>();

    /**
     * @return the guiceProviderModuleRegistry
     */
    public List<Module> getModules()
    {
        return modules;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings( "unchecked" )
    public void handle( GuiceProvidedModules annotation, Method method )
        throws HandleException
    {
        final Class<?> returnType = method.getReturnType();

        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( format( "  Found %s annotated method, checking if return type '%s' is one of ( %s | Iterable<%s> | %s[] )",
                                  GuiceProvidedModules.class.getSimpleName(),
                                  returnType.getName(),
                                  Module.class.getName(),
                                  Module.class.getName(),
                                  Module.class.getName() ) );
        }

        if ( !Modifier.isPublic( method.getModifiers() ) || !Modifier.isStatic( method.getModifiers() ) )
        {
            throw new HandleException( "Impossible to invoke method: " + method + ", it has to be static and public" );
        }

        final Class<?> type = method.getDeclaringClass();

        try
        {
            if ( Module.class.isAssignableFrom( returnType ) )
            {
                modules.add( (Module) method.invoke( type ) );
            }
            else if ( new TypeLiteral<Iterable<Module>>()
            {
            }.getRawType().isAssignableFrom( returnType ) )
            {
                addModules( (Iterable<Module>) method.invoke( type ) );
            }
            else if ( new TypeLiteral<Module[]>()
            {
            }.getRawType().isAssignableFrom( returnType ) )
            {
                addModules( (Module[]) method.invoke( type ) );
            }
            else
            {
                throw new ClassCastException( format( "  Incompatible return type: %s.\nThe return type must be one of ( %s | Iterable<%s> | %s[] )",
                                                      returnType.getName(),
                                                      Module.class.getName(),
                                                      Module.class.getName(),
                                                      Module.class.getName() ) );
            }

            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "  Invoked method: " + method.toGenericString() );
            }
        }
        catch ( Exception e )
        {
            throw new HandleException( e );
        }
    }

    private void addModules( Iterable<Module> modules )
    {
        for ( Module module : modules )
        {
            this.modules.add( module );
        }
    }

    private void addModules( Module... modules )
    {
        Collections.addAll( this.modules, modules );
    }
}
