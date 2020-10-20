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
package org.apache.james.mailbox.store.mail.model;

/**
 * <p>Conventional meta-data names for standard properties.</p>
 * <h4>Conventions</h4>
 * <p>
 * The domain containing the namespace should be controlled by the definer.
 * So, all properties defined here begin with <code>http://james.apache.org</code>.
 * This is a simple way to prevent namespace collisions with expansion terms.
 * </p><p>
 * Local names are lower case.
 * </p><p>
 * Namespaces derived from the RFC in which the term is 
 * defined are preferred. So, terms defined in
 * <a href='http://james.apache.org/server/rfclist/basic/rfc2045.txt'>RFC2045</a>
 * would be based in the <code>http://james.apache.org/rfc2045</code> space.
 * </p><ul>
 * <li>
 * <strong>For unstructured fields</strong> this base is used directly as the namespace.
 * </li><li>
 * <strong>For structure fields</strong>, the capitalised name (as used in the RFC text)
 * is suffixed to this base.
 * <ul>
 * <li>
 * <strong>Elements</strong> are named from the grammar.
 * </li>
 * <li>
 * <strong>Parameters of structured fields</strong> use a namespace rooted in this 
 * base suffixed with <em>params</em>. This namespace should contains only those 
 * parameters. The name of each property is the name of the parameter, converted 
 * to lower case. So, by iterating through all properties in the sufffixed namespace, 
 * every parameter can be named and value.
 * </li>
 * </ul>
 * </li>
 * </ul>
 * <h4>Examples</h4>
 * <h5>Content-Type</h5>
 * <code>Content-Type</code> is defined in <code>RFC2045</code>.
 * So, the namespaces are based in <code>http://james.apache.org/rfc2045/</code>.
 * It is a structure field with parameters.  It's direct properties are spaced in
 * <code>http://james.apache.org/rfc2045/Content-Type/</code>. 
 * It's parameters are spaced in 
 * <code>http://james.apache.org/rfc2045/Content-Type/params</code>.
 * </p><p>
 * So, for <code>Content-Type: text/plain ; charset=us-ascii</code>:
 * </p>
 * <table>
 * <th><td>Namespace</td><td>Name</td><td>Value</td></th>
 * <tr><td>http://james.apache.org/rfc2045/Content-Type/</td><td>type</td><td>text</td></tr>
 * <tr><td>http://james.apache.org/rfc2045/Content-Type/</td><td>subtype</td><td>plain</td></tr>
 * <tr><td>http://james.apache.org/rfc2045/Content-Type/params</td><td>charset</td><td>us-ascii</td></tr>
 * </table>
 */
public class StandardNames {
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a>.
     */
    public static final String NAMESPACE_RFC_2045 = "http://james.apache.org/rfc2045";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a>.
     */
    public static final String MIME_CONTENT_TYPE_SPACE = NAMESPACE_RFC_2045 + "/Content-Type";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Type
     * parameter space.
     */
    public static final String MIME_CONTENT_TYPE_PARAMETER_SPACE = MIME_CONTENT_TYPE_SPACE + "/params";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Type
     * "charset" parameter.
     */
    public static final String MIME_CONTENT_TYPE_PARAMETER_CHARSET_NAME = "charset";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> mime type properties.
     * A distinct namespace is required to distinguish these properties from the Content-Type
     * parameters.
     * @see #NAMESPACE_RFC_2045
     */
    public static final String MIME_MIME_TYPE_SPACE = MIME_CONTENT_TYPE_SPACE;
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> media type property
     */
    public static final String MIME_MEDIA_TYPE_NAME = "type";
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> sub type property
     */
    public static final String MIME_SUB_TYPE_NAME = "subtype";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-ID property.
     * @see #MIME_CONTENT_TYPE_SPACE
     */
    public static final String MIME_CONTENT_ID_SPACE = NAMESPACE_RFC_2045;
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-ID property
     */
    public static final String MIME_CONTENT_ID_NAME = "Content-ID";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Description property.
     * @see #NAMESPACE_RFC_2045
     */
    public static final String MIME_CONTENT_DESCRIPTION_SPACE = NAMESPACE_RFC_2045;
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Description property
     */
    public static final String MIME_CONTENT_DESCRIPTION_NAME = "Content-Description";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Transfer-Encoding property.
     * @see #NAMESPACE_RFC_2045
     */
    public static final String MIME_CONTENT_TRANSFER_ENCODING_SPACE = NAMESPACE_RFC_2045;
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2045.html'>MIME</a> Content-Transfer-Encoding property
     */
    public static final String MIME_CONTENT_TRANSFER_ENCODING_NAME = "Content-Transfer-Encoding";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2557.html'>MIME</a>.
     */
    public static final String NAMESPACE_RFC_2557 = "http://james.apache.org/rfc2557";
    
    /**
     * Namespace for <a href='http://www.faqs.org/rfcs/rfc2557.html'>RFC2557</a> Content-Location property.
     * @see #NAMESPACE_RFC_2557
     */
    public static final String MIME_CONTENT_LOCATION_SPACE = NAMESPACE_RFC_2557;
    
    /**
     * Local name for <a href='http://www.faqs.org/rfcs/rfc2557.html'>RFC2557</a> Content-Location property
     */
    public static final String MIME_CONTENT_LOCATION_NAME = "Content-Location";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc1864.html'>RFC 1864 - The Content-MD5 Header Field</a>.
    */
   public static final String NAMESPACE_RFC_1864 = "http://james.apache.org/rfc1864";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc1864.html'>RFC1864</a> Content-MD5 property.
    * @see #NAMESPACE_RFC_1864
    */
   public static final String MIME_CONTENT_MD5_SPACE = NAMESPACE_RFC_1864;
   
   /**
    * Local name for <a href='http://www.faqs.org/rfcs/rfc1864.html'>RFC1864</a> Content-MD5 property
    */
   public static final String MIME_CONTENT_MD5_NAME = "Content-MD5";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc1766.html'>RFC 1766 - Tags for the Identification of Languages</a>.
    */
   public static final String NAMESPACE_RFC_1766 = "http://james.apache.org/rfc1766";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc1766.html'>RFC1766</a> Content-Language property.
    * @see #NAMESPACE_RFC_1766
    */
   public static final String MIME_CONTENT_LANGUAGE_SPACE = NAMESPACE_RFC_1766;
   
   /**
    * Local name for <a href='http://www.faqs.org/rfcs/rfc1864.html'>RFC1864</a> Content-Language property
    */
   public static final String MIME_CONTENT_LANGUAGE_NAME = "Content-Language";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC 2183- 
    * Communicating Presentation Information in Internet Messages</a>.
    */
   public static final String NAMESPACE_RFC_2183 = "http://james.apache.org/rfc2183";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a> Content-Disposition property.
    * @see #NAMESPACE_RFC_2183
    */
   public static final String MIME_CONTENT_DISPOSITION_SPACE = NAMESPACE_RFC_2183 + "Content-Disposition";
   
   /**
    * Local name for <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a> Content-Disposition property
    */
   public static final String MIME_CONTENT_DISPOSITION_TYPE_NAME = "disposition-type";
   
   /**
    * Namespace for <a href='http://www.faqs.org/rfcs/rfc2183.html'>RFC2183</a> Content-Disposition property.
    * @see #NAMESPACE_RFC_2183
    */
   public static final String MIME_CONTENT_DISPOSITION_PARAMETER_SPACE = MIME_CONTENT_DISPOSITION_SPACE + "/params";
}
