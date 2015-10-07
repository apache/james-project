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

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Serialise the email and pass it to an HTTP call
 * 
 * Sample configuration:
 * 
 <mailet match="All" class="HeadersToHTTP">
   <url>http://192.168.0.252:3000/alarm</url>
   <parameterKey>Test</parameterKey>
   <parameterValue>ParameterValue</parameterValue>
   <passThrough>true</passThrough>
 </mailet>

 * 
 */
public class HeadersToHTTP extends GenericMailet {

	/**
	 * The name of the header to be added.
	 */
	private String url;
	private String parameterKey = null;
	private String parameterValue = null;
	private boolean passThrough = true;

	/**
	 * Initialize the mailet.
	 */
	public void init() throws MessagingException {

		passThrough = (getInitParameter("passThrough", "true").compareToIgnoreCase("true") == 0);
		String targetUrl = getInitParameter("url");
		parameterKey = getInitParameter("parameterKey");
		parameterValue = getInitParameter("parameterValue");

		// Check if needed config values are used
		if (targetUrl == null || targetUrl.equals("")) {
			throw new MessagingException("Please configure a targetUrl (\"url\")");
		} else {
			try {
				// targetUrl = targetUrl + ( targetUrl.contains("?") ? "&" :
				// "?") + parameterKey + "=" + parameterValue;
				url = new URL(targetUrl).toExternalForm();
			} catch (MalformedURLException e) {
				throw new MessagingException(
						"Unable to contruct URL object from url");
			}
		}

		// record the result
		log("I will attempt to deliver serialised messages to "
				+ targetUrl
				+ ". "
				+ ( ((parameterKey==null) || (parameterKey.length()<1)) ? "I will not add any fields to the post. " : "I will prepend: "	+ parameterKey + "=" + parameterValue + ". ")
				+ (passThrough ? "Messages will pass through." : "Messages will be ghosted."));
	}

	/**
	 * Takes the message, serialises it and sends it to the URL
	 * 
	 * @param mail
	 *            the mail being processed
	 * 
	 */
	public void service(Mail mail) {
		try {
			log(mail.getName() + "HeadersToHTTP: Starting");
			MimeMessage message = mail.getMessage();
			HashSet<NameValuePair> pairs = getNameValuePairs(message);
			log(mail.getName() + "HeadersToHTTP: " + pairs.size() + " named value pairs found");
			String result = httpPost(pairs);
			if (passThrough) {
				addHeader(mail, true, result);
			} else {
				mail.setState(Mail.GHOST);
			}
		} catch (javax.mail.MessagingException me) {
			log(me.getMessage());
			addHeader(mail, false, me.getMessage());
		} catch (IOException e) {
			log(e.getMessage());
			addHeader(mail, false, e.getMessage());
		}
	}

	private void addHeader(Mail mail, boolean success, String errorMessage) {
		try {
			MimeMessage message = mail.getMessage();
			message.setHeader("X-headerToHTTP", (success ? "Succeeded" : "Failed"));
			if (!success && errorMessage!=null && errorMessage.length()>0) {
				message.setHeader("X-headerToHTTPFailure", errorMessage);
			}
			message.saveChanges();
		} catch (MessagingException e) {
			log(e.getMessage());
		}
	}

	private String httpPost(HashSet<NameValuePair> pairs) throws IOException {

		HttpClient client = new DefaultHttpClient();
		HttpPost post = new HttpPost(url);

		post.setParams(extractParams(pairs));
		
		HttpResponse clientResponse = client.execute(post);
		String result = clientResponse.getStatusLine().getStatusCode() + ": " + clientResponse.getStatusLine();
		log("HeadersToHTTP: " + result);

        client.getConnectionManager().shutdown();
		return result;
		
	}

	private HttpParams extractParams(HashSet<NameValuePair> pairs) {
		HttpParams params =  new BasicHttpParams();
		for(NameValuePair p : pairs) {
			params.setParameter(p.getName(),p.getValue());
		}
		return params;
	}
	
	private HashSet<NameValuePair> getNameValuePairs(MimeMessage message) throws UnsupportedEncodingException, MessagingException {

		// to_address
		// from
		// reply to
		// subject
		
		HashSet<NameValuePair> pairs = new HashSet<NameValuePair>();
		
    	if (message!=null) {
	    	if (message.getSender()!=null) {
	    		pairs.add( new BasicNameValuePair( "from", message.getSender().toString() ) );
	    	}
	    	if (message.getReplyTo()!=null) {
	    		pairs.add( new BasicNameValuePair( "reply_to", Arrays.toString(message.getReplyTo())) );
	    	}
	    	if (message.getMessageID()!=null) {
	    		pairs.add( new BasicNameValuePair( "message_id", message.getMessageID() ) );
	    	}
	    	if (message.getSubject()!=null) {
	    		pairs.add( new BasicNameValuePair( "subject", message.getSubject() ) );
	    	}
	    	pairs.add( new BasicNameValuePair( "size", Integer.toString(message.getSize()) ) );
	    }

		pairs.add( new BasicNameValuePair( parameterKey, parameterValue) );
    			
    	return pairs;
    }

	/**
	 * Return a string describing this mailet.
	 * 
	 * @return a string describing this mailet
	 */
	public String getMailetInfo() {
		return "HTTP POST serialised message";
	}

}
