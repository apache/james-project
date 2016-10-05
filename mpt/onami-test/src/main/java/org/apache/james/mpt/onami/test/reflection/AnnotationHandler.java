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

package org.apache.james.mpt.onami.test.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Interface to specify a generic annotation handler.
 *
 * @param <A> whatever annotation type
 * @param <E> the element annotated with an annotation type
 */
public interface AnnotationHandler<A extends Annotation, E extends AnnotatedElement>
{

    /**
     * Invoked when {@link ClassVisitor} found an annotation into a class.
     *
     * @param annotation handled annotation
     * @param element the element annotated with input annotation
     * @throws HandleException if an error occurs while processing the annotated element
     *         and the related annotation
     */
    void handle( A annotation, E element )
        throws HandleException;

}
