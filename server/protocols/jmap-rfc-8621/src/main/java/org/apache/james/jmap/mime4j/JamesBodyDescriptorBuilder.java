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

package org.apache.james.jmap.mime4j;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.james.jmap.mail.Email;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.FieldParser;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.field.DefaultFieldParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.BodyDescriptorBuilder;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.RawField;
import org.apache.james.mime4j.util.MimeUtil;

/**
 * Default {@link BodyDescriptorBuilder} implementation.
 *
 * See https://github.com/apache/james-mime4j/pull/94
 */
public class JamesBodyDescriptorBuilder implements BodyDescriptorBuilder {

    private static final String CONTENT_TYPE = FieldName.CONTENT_TYPE.toLowerCase(Locale.US);

    private static final String SUB_TYPE_EMAIL = "rfc822";
    private static final String MEDIA_TYPE_TEXT = "text";
    private static final String MEDIA_TYPE_MESSAGE = "message";
    private static final String EMAIL_MESSAGE_MIME_TYPE = MEDIA_TYPE_MESSAGE + "/" + SUB_TYPE_EMAIL;
    private static final String DEFAULT_SUB_TYPE = "plain";
    private static final String DEFAULT_MEDIA_TYPE = MEDIA_TYPE_TEXT;
    private static final String DEFAULT_MIME_TYPE = DEFAULT_MEDIA_TYPE + "/" + DEFAULT_SUB_TYPE;

    private final String parentMimeType;
    private final DecodeMonitor monitor;
    private final FieldParser<? extends ParsedField> fieldParser;
    private final Map<String, ParsedField> fields;
    private Charset defaultCharset = Email.defaultCharset();

    /**
     * Creates a new root <code>BodyDescriptor</code> instance.
     */
    public JamesBodyDescriptorBuilder() {
        this(null);
    }

    public JamesBodyDescriptorBuilder(final String parentMimeType) {
        this(parentMimeType, null, null);
    }

    /**
     * Creates a new <code>BodyDescriptor</code> instance.
     */
    public JamesBodyDescriptorBuilder(
            final String parentMimeType,
            final FieldParser<? extends ParsedField> fieldParser,
            final DecodeMonitor monitor) {
        super();
        this.parentMimeType = parentMimeType;
        this.fieldParser = fieldParser != null ? fieldParser : DefaultFieldParser.getParser();
        this.monitor = monitor != null ? monitor : DecodeMonitor.SILENT;
        this.fields = new HashMap<String, ParsedField>();
    }

    public void setDefaultCharset(Charset charset) {
        this.defaultCharset = charset;
    }

    public void reset() {
        fields.clear();
    }

    public Field addField(final RawField rawfield) throws MimeException {
        ParsedField field = fieldParser.parse(rawfield, monitor);
        String name = field.getNameLowerCase();
        if (field.bodyDescriptionField() && !fields.containsKey(name)) {
            fields.put(name, field);
        }
        return field;
    }

    public BodyDescriptor build() {
        String actualMimeType = null;
        String actualMediaType = null;
        String actualSubType = null;
        String actualCharset = null;
        String actualBoundary = null;
        ContentTypeField contentTypeField = (ContentTypeField) fields.get(CONTENT_TYPE);
        if (contentTypeField != null) {
            actualMimeType = contentTypeField.getMimeType();
            actualMediaType = contentTypeField.getMediaType();
            actualSubType = contentTypeField.getSubType();
            actualCharset = contentTypeField.getCharset();
            actualBoundary = contentTypeField.getBoundary();

            boolean multipart = actualMediaType != null && actualMediaType.equalsIgnoreCase("multipart");
            if (multipart && actualBoundary == null) {
                actualMimeType = null;
                actualMediaType = null;
                actualSubType = null;
            }
        }
        if (actualMimeType == null) {
            if (MimeUtil.isSameMimeType("multipart/digest", parentMimeType)) {
                actualMimeType = EMAIL_MESSAGE_MIME_TYPE;
                actualMediaType = MEDIA_TYPE_MESSAGE;
                actualSubType = SUB_TYPE_EMAIL;
            } else {
                actualMimeType = DEFAULT_MIME_TYPE;
                actualMediaType = DEFAULT_MEDIA_TYPE;
                actualSubType = DEFAULT_SUB_TYPE;
            }
        }
        if (actualCharset == null && MEDIA_TYPE_TEXT.equals(actualMediaType)) {
            actualCharset = defaultCharset.name();
        }
        if (!MimeUtil.isMultipart(actualMimeType)) {
            actualBoundary = null;
        }
        return new MaximalBodyDescriptor(
                actualMimeType, actualMediaType, actualSubType, actualBoundary, actualCharset,
                fields);
    }

    public BodyDescriptorBuilder newChild() {
        String actualMimeType;
        ContentTypeField contentTypeField = (ContentTypeField) fields.get(CONTENT_TYPE);
        if (contentTypeField != null) {
            actualMimeType = contentTypeField.getMimeType();
        } else {
            if (MimeUtil.isSameMimeType("multipart/digest", parentMimeType)) {
                actualMimeType = EMAIL_MESSAGE_MIME_TYPE;
            } else {
                actualMimeType = DEFAULT_MIME_TYPE;
            }
        }
        return new JamesBodyDescriptorBuilder(actualMimeType, fieldParser, monitor);
    }

}
