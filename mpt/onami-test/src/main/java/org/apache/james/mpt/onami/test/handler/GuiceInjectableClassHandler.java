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

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.reflection.AnnotationHandler;
import org.apache.james.mpt.onami.test.reflection.HandleException;

/**
 * Handler class to handle all {@link com.google.inject.Inject} and {@link javax.inject.Inject} annotations.
 *
 * @param <A> whatever annotation type
 * @see org.apache.onami.test.reflection.ClassVisitor
 */
public final class GuiceInjectableClassHandler<A extends Annotation>
    implements AnnotationHandler<A, AccessibleObject>
{

    private static final Logger LOGGER = Logger.getLogger( GuiceInjectableClassHandler.class.getName() );

    /**
     * Contains the guice injectable classes founded, after inspection.
     */
    protected final Set<Class<?>> classes = new HashSet<Class<?>>();

    /**
     * Return all {@link Class} that contains at last one {@link com.google.inject.Inject} or
     * {@link javax.inject.Inject} annotation.
     *
     * @return {@link Class} array.
     */
    public Class<?>[] getClasses()
    {
        return classes.toArray( new Class<?>[classes.size()] );
    }

    /**
     * {@inheritDoc}
     */
    public void handle( A annotation, AccessibleObject element )
        throws HandleException
    {
        Class<?> type = null;

        if ( element instanceof Member )
        {
            type = ( (Member) element ).getDeclaringClass();
        }

        if ( type != null && !classes.contains( type ) )
        {
            if ( LOGGER.isLoggable( Level.FINER ) )
            {
                LOGGER.finer( "   Found injectable type: " + type );
            }
            classes.add( type );
        }
    }

}
