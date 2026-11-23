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

package org.apache.james.transport.mailets;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.codec.DecoderException;

import com.google.common.collect.ImmutableList;

import net.fortuna.ical4j.data.DefaultPropertyFactorySupplier;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.PropertyFactory;
import net.fortuna.ical4j.model.parameter.Encoding;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.util.DecoderFactory;

/**
 * Builds ATTACH properties whose inline (ENCODING) value is kept on the heap.
 *
 * ical4j 4.4.0 decodes it into a new temporary file it maps in memory, and only deletes on JVM exit: every parsed
 * ATTACH would leave a file in java.io.tmpdir, and an entry in the JVM wide set of files to delete on exit.
 */
public class InMemoryAttachFactory extends Attach.Factory {
    private static final long serialVersionUID = 1L;
    // The ical4j factories hold no state: they are shared by all the parsed properties
    private static final List<PropertyFactory<?>> PROPERTY_FACTORIES = Stream.concat(
            Stream.<PropertyFactory<?>>of(new InMemoryAttachFactory()),
            new DefaultPropertyFactorySupplier().get().stream())
        .collect(ImmutableList.toImmutableList());

    /**
     * The ical4j property factories, ATTACH being built by this factory.
     */
    public static List<PropertyFactory<?>> propertyFactories() {
        return PROPERTY_FACTORIES;
    }

    /**
     * An ATTACH holding a URI, or a value ical4j cannot decode, is built by ical4j, which creates no file for them.
     */
    @Override
    public Attach createProperty(ParameterList parameters, String value) {
        Optional<Encoding> encoding = parameters.getParameter(Parameter.ENCODING);
        if (encoding.isEmpty()) {
            return new Attach(parameters, value);
        }
        try {
            byte[] decoded = DecoderFactory.getInstance().createBinaryDecoder(encoding.get())
                .decode(value.getBytes(StandardCharsets.US_ASCII));
            return new Attach(parameters, ByteBuffer.wrap(decoded));
        } catch (UnsupportedEncodingException | DecoderException e) {
            return new Attach(parameters, value);
        }
    }
}
