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

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.james.mime4j.dom.field.ContentDescriptionField;
import org.apache.james.mime4j.dom.field.ContentDispositionField;
import org.apache.james.mime4j.dom.field.ContentIdField;
import org.apache.james.mime4j.dom.field.ContentLanguageField;
import org.apache.james.mime4j.dom.field.ContentLengthField;
import org.apache.james.mime4j.dom.field.ContentLocationField;
import org.apache.james.mime4j.dom.field.ContentMD5Field;
import org.apache.james.mime4j.dom.field.ContentTransferEncodingField;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.dom.field.FieldName;
import org.apache.james.mime4j.dom.field.MimeVersionField;
import org.apache.james.mime4j.dom.field.ParsedField;
import org.apache.james.mime4j.field.MimeVersionFieldImpl;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.util.MimeUtil;

/**
 * Extended {@link BodyDescriptor} implementation with complete content details.
 *
 * See https://github.com/apache/james-mime4j/pull/94
 */
public class MaximalBodyDescriptor implements BodyDescriptor {

    private static final String CONTENT_TYPE = FieldName.CONTENT_TYPE.toLowerCase(Locale.US);
    private static final String CONTENT_LENGTH = FieldName.CONTENT_LENGTH.toLowerCase(Locale.US);
    private static final String CONTENT_TRANSFER_ENCODING = FieldName.CONTENT_TRANSFER_ENCODING.toLowerCase(Locale.US);
    private static final String CONTENT_DISPOSITION = FieldName.CONTENT_DISPOSITION.toLowerCase(Locale.US);
    private static final String CONTENT_ID = FieldName.CONTENT_ID.toLowerCase(Locale.US);
    private static final String CONTENT_MD5 = FieldName.CONTENT_MD5.toLowerCase(Locale.US);
    private static final String CONTENT_DESCRIPTION = FieldName.CONTENT_DESCRIPTION.toLowerCase(Locale.US);
    private static final String CONTENT_LANGUAGE = FieldName.CONTENT_LANGUAGE.toLowerCase(Locale.US);
    private static final String CONTENT_LOCATION = FieldName.CONTENT_LOCATION.toLowerCase(Locale.US);
    private static final String MIME_VERSION = FieldName.MIME_VERSION.toLowerCase(Locale.US);

    private final String mediaType;
    private final String subType;
    private final String mimeType;
    private final String boundary;
    private final String charset;
    private final Map<String, ParsedField> fields;

    MaximalBodyDescriptor(
            final String mimeType,
            final String mediaType,
            final String subType,
            final String boundary,
            final String charset,
            final Map<String, ParsedField> fields) {
        super();

        this.mimeType = mimeType;
        this.mediaType = mediaType;
        this.subType = subType;
        this.boundary = boundary;
        this.charset = charset;
        this.fields = fields != null ? new HashMap<String, ParsedField>(fields) :
            Collections.<String, ParsedField>emptyMap();
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getBoundary() {
        return boundary;
    }

    public String getCharset() {
        return charset;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getSubType() {
        return subType;
    }

    public Map<String, String> getContentTypeParameters() {
        ContentTypeField contentTypeField = (ContentTypeField) fields.get(CONTENT_TYPE);
        return contentTypeField != null ? contentTypeField.getParameters() :
            Collections.<String, String>emptyMap();
    }

    public String getTransferEncoding() {
        ContentTransferEncodingField contentTransferEncodingField =
            (ContentTransferEncodingField) fields.get(CONTENT_TRANSFER_ENCODING);
        return contentTransferEncodingField != null ? contentTransferEncodingField.getEncoding() :
            MimeUtil.ENC_7BIT;
    }

    public long getContentLength() {
        ContentLengthField contentLengthField = (ContentLengthField) fields.get(CONTENT_LENGTH);
        return contentLengthField != null ? contentLengthField.getContentLength() : -1;
    }

    /**
     * Gets the MIME major version
     * as specified by the <code>MIME-Version</code>
     * header.
     * Defaults to one.
     * @return positive integer
     */
    public int getMimeMajorVersion() {
        MimeVersionField mimeVersionField = (MimeVersionField) fields.get(MIME_VERSION);
        return mimeVersionField != null ? mimeVersionField.getMajorVersion() :
            MimeVersionFieldImpl.DEFAULT_MAJOR_VERSION;
    }

    /**
     * Gets the MIME minor version
     * as specified by the <code>MIME-Version</code>
     * header.
     * Defaults to zero.
     * @return positive integer
     */
    public int getMimeMinorVersion() {
        MimeVersionField mimeVersionField = (MimeVersionField) fields.get(MIME_VERSION);
        return mimeVersionField != null ? mimeVersionField.getMinorVersion() :
            MimeVersionFieldImpl.DEFAULT_MINOR_VERSION;
    }


    /**
     * Gets the value of the <a href='http://www.faqs.org/rfcs/rfc2045'>RFC</a>
     * <code>Content-Description</code> header.
     * @return value of the <code>Content-Description</code> when present,
     * null otherwise
     */
    public String getContentDescription() {
        ContentDescriptionField contentDescriptionField =
            (ContentDescriptionField) fields.get(CONTENT_DESCRIPTION);
        return contentDescriptionField != null ? contentDescriptionField.getDescription() : null;
    }

    /**
     * Gets the value of the <a href='http://www.faqs.org/rfcs/rfc2045'>RFC</a>
     * <code>Content-ID</code> header.
     * @return value of the <code>Content-ID</code> when present,
     * null otherwise
     */
    public String getContentId() {
        ContentIdField contentIdField = (ContentIdField) fields.get(CONTENT_ID);
        return contentIdField != null ? contentIdField.getId() : null;
    }

    /**
     * Gets the disposition type of the <code>content-disposition</code> field.
     * The value is case insensitive and will be converted to lower case.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return content disposition type,
     * or null when this has not been set
     */
    public String getContentDispositionType() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getDispositionType() : null;
    }

    /**
     * Gets the parameters of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return parameter value strings indexed by parameter name strings,
     * not null
     */
    public Map<String, String> getContentDispositionParameters() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getParameters() :
            Collections.<String, String>emptyMap();
    }

    /**
     * Gets the <code>filename</code> parameter value of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return filename parameter value,
     * or null when it is not present
     */
    public String getContentDispositionFilename() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getFilename() : null;
    }

    /**
     * Gets the <code>modification-date</code> parameter value of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return modification-date parameter value,
     * or null when this is not present
     */
    public Date getContentDispositionModificationDate() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getModificationDate() : null;
    }

    /**
     * Gets the <code>creation-date</code> parameter value of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return creation-date parameter value,
     * or null when this is not present
     */
    public Date getContentDispositionCreationDate() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getCreationDate() : null;
    }

    /**
     * Gets the <code>read-date</code> parameter value of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return read-date parameter value,
     * or null when this is not present
     */
    public Date getContentDispositionReadDate() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getReadDate() : null;
    }

    /**
     * Gets the <code>size</code> parameter value of the <code>content-disposition</code> field.
     * See <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a>.
     * @return size parameter value,
     * or -1 if this size has not been set
     */
    public long getContentDispositionSize() {
        ContentDispositionField contentDispositionField =
            (ContentDispositionField) fields.get(CONTENT_DISPOSITION);
        return contentDispositionField != null ? contentDispositionField.getSize() : -1;
    }

    /**
     * Get the <code>content-language</code> header values.
     * Each applicable language tag will be returned in order.
     * See <a href='http://tools.ietf.org/html/rfc4646'>RFC4646</a>
     * <cite>http://tools.ietf.org/html/rfc4646</cite>.
     * @return list of language tag Strings,
     * or null if no header exists
     */
    public List<String> getContentLanguage() {
        ContentLanguageField contentLanguageField =
            (ContentLanguageField) fields.get(CONTENT_LANGUAGE);
        return contentLanguageField != null ? contentLanguageField.getLanguages() :
            Collections.<String>emptyList();
    }

    /**
     * Get the <code>content-location</code> header value.
     * See <a href='http://tools.ietf.org/html/rfc2557'>RFC2557</a>
     * @return the URL content-location
     * or null if no header exists
     */
    public String getContentLocation() {
        ContentLocationField contentLocationField =
            (ContentLocationField) fields.get(CONTENT_LOCATION);
        return contentLocationField != null ? contentLocationField.getLocation() : null;
    }

    /**
     * Gets the raw, Base64 encoded value of the
     * <code>Content-MD5</code> field.
     * See <a href='http://tools.ietf.org/html/rfc1864'>RFC1864</a>.
     * @return raw encoded content-md5
     * or null if no header exists
     */
    public String getContentMD5Raw() {
        ContentMD5Field contentMD5Field = (ContentMD5Field) fields.get(CONTENT_MD5);
        return contentMD5Field != null ? contentMD5Field.getMD5Raw() : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[mimeType=");
        sb.append(mimeType);
        sb.append(", mediaType=");
        sb.append(mediaType);
        sb.append(", subType=");
        sb.append(subType);
        sb.append(", boundary=");
        sb.append(boundary);
        sb.append(", charset=");
        sb.append(charset);
        sb.append("]");
        return sb.toString();
    }

}
