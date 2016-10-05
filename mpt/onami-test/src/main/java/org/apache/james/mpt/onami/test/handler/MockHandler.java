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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.Mock;
import org.apache.james.mpt.onami.test.mock.MockEngine;
import org.apache.james.mpt.onami.test.reflection.FieldHandler;
import org.apache.james.mpt.onami.test.reflection.HandleException;


/**
 * Handler class to handle all {@link Mock} annotations.
 *
 * @see org.apache.onami.test.reflection.ClassVisitor
 * @see Mock
 */
public final class MockHandler
    implements FieldHandler<Mock>
{

    private static final Logger LOGGER = Logger.getLogger( MockHandler.class.getName() );

    private final HashMap<Field, Object> mockedObjects = new HashMap<Field, Object>( 1 );

    /**
     * Return the mocked objects.
     * 
     * @param engine the {@link MockEngine}
     * @return the map of mocked objects
     */
    public HashMap<Field, Object> getMockedObject( MockEngine engine )
    {
        createMockedObjectBymockFramekork( engine );
        return mockedObjects;
    }

    private void createMockedObjectBymockFramekork( MockEngine engine )
    {
        for ( Entry<Field, Object> entry : mockedObjects.entrySet() )
        {
            if ( entry.getValue() instanceof Class<?> )
            {
                Field field = entry.getKey();
                Mock mock = field.getAnnotation( Mock.class );
                mockedObjects.put( entry.getKey(), engine.createMock( (Class<?>) entry.getValue(), mock.type() ) );
            }
        }
    }

    
    /**
     * Invoked when the visitor founds an element with a {@link Mock} annotation.
     * @param annotation The {@link Mock} annotation type
     * @param element the {@link Mock} annotated fiels 
     * @throws HandleException when an error occurs.    
     */
    @SuppressWarnings( "unchecked" )
    public void handle( final Mock annotation, final Field element )
        throws HandleException
    {
        final Class<? super Object> type = (Class<? super Object>) element.getDeclaringClass();

        if ( LOGGER.isLoggable( Level.FINER ) )
        {
            LOGGER.finer( "      Found annotated field: " + element );
        }
        if ( annotation.providedBy().length() > 0 )
        {
            Class<?> providedClass = type;
            if ( annotation.providerClass() != Object.class )
            {
                providedClass = annotation.providerClass();
            }
            try
            {
                Method method = providedClass.getMethod( annotation.providedBy() );

                if ( !element.getType().isAssignableFrom( method.getReturnType() ) )
                {
                    throw new HandleException( "Impossible to mock %s due to compatibility type, method provider %s#%s returns %s",
                                               element.getDeclaringClass().getName(),
                                               providedClass.getName(),
                                               annotation.providedBy(),
                                               method.getReturnType().getName() );
                }
                try
                {
                    Object mocked = getMockProviderForType( element.getType(), method, type );
                    mockedObjects.put( element, mocked );
                }
                catch ( Throwable t )
                {
                    throw new HandleException( "Impossible to mock %s, method provider %s#%s raised an error: %s",
                                               element.getDeclaringClass().getName(),
                                               providedClass.getName(),
                                               annotation.providedBy(),
                                               t );
                }
            }
            catch ( SecurityException e )
            {
                throw new HandleException( "Impossible to mock %s, impossible to access to method provider %s#%s: %s",
                                           element.getDeclaringClass().getName(),
                                           providedClass.getName(),
                                           annotation.providedBy(),
                                           e );
            }
            catch ( NoSuchMethodException e )
            {
                throw new HandleException( "Impossible to mock %s, the method provider %s#%s doesn't exist.",
                                           element.getDeclaringClass().getName(),
                                           providedClass.getName(),
                                           annotation.providedBy() );
            }
        }
        else
        {
            mockedObjects.put( element, element.getType() );
        }
    }

    @SuppressWarnings( "unchecked" )
    private <T> T getMockProviderForType( T t, Method method, Class<?> cls )
        throws HandleException
    {
        if ( method.getReturnType() == t )
        {
            try
            {
                if ( LOGGER.isLoggable( Level.FINER ) )
                {
                    LOGGER.finer( "        ...invoke Provider method for Mock: " + method.getName() );
                }
                if ( !Modifier.isPublic( method.getModifiers() ) || !Modifier.isStatic( method.getModifiers() ) )
                {
                    throw new HandleException( "Impossible to invoke method %s#%s. The method shuld be 'static public %s %s()",
                                               cls.getName(),
                                               method.getName(),
                                               method.getReturnType().getName(),
                                               method.getName() );
                }

                return (T) method.invoke( cls );
            }
            catch ( Exception e )
            {
                throw new RuntimeException( e );
            }
        }
        throw new HandleException( "The method: %s should return type %s", method, t );
    }

}
