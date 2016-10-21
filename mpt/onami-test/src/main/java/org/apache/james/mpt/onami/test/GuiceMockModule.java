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

import static com.google.common.base.Preconditions.checkState;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.james.mpt.onami.test.annotation.Mock;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

/**
 * <p>
 * This class creates the binding for all mock objects found.
 * </p>
 * <p>
 * Method {@link GuiceMockModule#configure()} creates a binding for each {@link Mock} annotation found. The binding will
 * be created <b>if and only if</b> there is no types conflict between {@link Mock} caught.
 * <p>
 * <p>
 * <b>A type conflict</b> is detected if two or more field are annotated with the same {@link Mock} and no different
 * {@link Mock#annotatedWith} parameter are specified, or two o more equals {@link Mock#annotatedWith} parameter are
 * specified for the same type field.
 * </p>
 * <p>
 * If a conflict is detected the binding will not create for the conflicted type, moreover the field will be injected
 * into the test class.
 * </p>
 */
public class GuiceMockModule extends AbstractModule {

    private static final Logger LOGGER = Logger.getLogger(GuiceMockModule.class.getName());

    final Map<Field, Object> mockedFields;

    /**
     * Costructor.
     *
     * @param mockedFields the map of mock fileds.
     */

    public GuiceMockModule(final Map<Field, Object> mockedFields) {
        this.mockedFields = mockedFields;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void configure() {
        final Multimap<Type, Field> fieldsByType = HashMultimap.create();

        for (final Entry<Field, Object> entry : this.mockedFields.entrySet()) {
            fieldsByType.put(entry.getKey().getGenericType(), entry.getKey());
        }

        for (final Type type : fieldsByType.keySet()) {
            final Collection<Field> fields = fieldsByType.get(type);

            boolean isTypeConflicts = false;
            if (fields.size() != 1) {
                isTypeConflicts = checkTypeConflict(fields);
            }

            checkState(!isTypeConflicts, "   Found multiple annotation @%s for type: %s; binding skipped!.",
                Mock.class.getSimpleName(), type);
            for (final Field field : fields) {
                @SuppressWarnings("rawtypes")
                final TypeLiteral literal = TypeLiteral.get(type);
                final Mock annoBy = field.getAnnotation(Mock.class);
                final Object mock = this.mockedFields.get(field);
                if (annoBy.annotatedWith() != Mock.NoAnnotation.class) {
                    bind(literal).annotatedWith(annoBy.annotatedWith()).toInstance(mock);
                } else if (!"".equals(annoBy.namedWith())) {
                    bind(literal).annotatedWith(Names.named(annoBy.namedWith())).toInstance(mock);
                } else {
                    bind(literal).toInstance(mock);
                }
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("    Created binding for: " + type + " " + annoBy);
                }
            }
        }
    }

    /**
     * @param fields
     * @return
     */
    private boolean checkTypeConflict(Collection<Field> fields) {
        final List<Class<?>> listAnnotatedType = new ArrayList<Class<?>>();
        final List<String> listNamedType = new ArrayList<String>();
        int numOfSimpleType = 0;

        for (Field field : fields) {
            final Mock annoBy = field.getAnnotation(Mock.class);

            if (annoBy.annotatedWith() == Mock.NoAnnotation.class && "".equals(annoBy.namedWith())) {
                numOfSimpleType++;
            }
            if (numOfSimpleType > 1) {
                LOGGER.finer("Found multiple simple type");
                return true;
            }

            if (annoBy.annotatedWith() != Mock.NoAnnotation.class) {
                if (!listAnnotatedType.contains(annoBy.annotatedWith())) {
                    listAnnotatedType.add(annoBy.annotatedWith());
                } else {
                    // found two fields with same annotation
                    LOGGER.finer("Found multiple annotatedBy type");
                    return true;
                }
            }

            if (!"".equals(annoBy.namedWith())) {
                if (!listNamedType.contains(annoBy.namedWith())) {
                    listNamedType.add(annoBy.namedWith());
                } else {
                    // found two fields with same named annotation
                    LOGGER.finer("Found multiple namedWith type");
                    return true;
                }
            }
        }
        return false;
    }

}
