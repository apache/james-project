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

import org.apache.mailet.base.StringUtils;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;

import javax.mail.MessagingException;
import javax.mail.internet.ContentType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Replace text contents
 * <p>This mailet allow to specific regular expression to replace text in subject and content.
 * 
 * <p>
 * Each expression is defined as:
 * <code>/REGEX_PATTERN/SUBSTITUTION_PATTERN/FLAGS/</code>
 * </p>
 * 
 * <p>
 * <code>REGEX_PATTERN</code> is a regex used for the match<br>
 * <code>SUBSTITUTION_PATTERN</code> is a substitution pattern<br>
 * <code>FLAGS</code> flags supported for the pattern:<br>
 *   i: case insensitive<br>
 *   m: multi line<br>
 *   x: extended (N/A)<br>
 *   r: repeat - keep matching until a substitution is possible<br>
 * </p>
 * 
 * <p>To identify subject and body pattern we use the tags &lt;subjectPattern&gt; and &lt;bodyPattern&gt;</p>
 *
 * <p>
 * Rules can be specified in external files.
 * Lines must be CRLF terminated and lines starting with # are considered commments.
 * Tags used to include external files are &lt;subjectPatternFile&gt; and 
 * &lt;bodyPatternFile&gt;
 * If file path starts with # then the file is loaded as a reasource.
 * </p>
 * 
 * <p>
 * Use of both files and direct patterns at the same time is allowed.
 * </p>
 * 
 * <p>
 * This mailet allow also to enforce the resulting charset for messages processed.
 * To do that the tag &lt;charset&gt; must be specified.
 * </p>
 * 
 * <p>
 * NOTE:
 * Regexp rules must be escaped by regexp excaping rules and applying this 2 additional rules:<br>
 * - "/" char inside an expression must be prefixed with "\":
 *   e.g: "/\//-//" replaces "/" with "-"<br>
 * - when the rules are specified using &lt;subjectPattern&gt; or &lt;bodyPattern&gt; and
 *   "/,/" has to be used in a pattern string it must be prefixed with a "\".
 *   E.g: "/\/\/,//" replaces "/" with "," (the rule would be "/\//,//" but the "/,/" must
 *   be escaped.<br>
 * </p>
 */
public class ReplaceContent extends GenericMailet {
    private static final String PARAMETER_NAME_SUBJECT_PATTERN = "subjectPattern";
    private static final String PARAMETER_NAME_BODY_PATTERN = "bodyPattern";
    private static final String PARAMETER_NAME_SUBJECT_PATTERNFILE = "subjectPatternFile";
    private static final String PARAMETER_NAME_BODY_PATTERNFILE = "bodyPatternFile";
    private static final String PARAMETER_NAME_CHARSET = "charset";
    
    public static final int FLAG_REPEAT = 1;
    
    private static class ReplaceConfig {
        private Pattern[] subjectPatterns;
        private String[] subjectSubstitutions;
        private Integer[] subjectFlags;
        private Pattern[] bodyPatterns;
        private String[] bodySubstitutions;
        private Integer[] bodyFlags;
    }
    
    private String charset;
    private int debug = 0;
    
    /**
     * returns a String describing this mailet.
     * 
     * @return A desciption of this mailet
     */
    public String getMailetInfo() {
        return "ReplaceContent";
    }

    /**
     * @return an array containing Pattern and Substitution of the input stream
     * @throws MailetException 
     */
    protected static PatternBean getPattern(String line) throws MailetException {
        String[] pieces = StringUtils.split(line, "/");
        if (pieces.length < 3) throw new MailetException("Invalid expression: " + line);
        int options = 0;
        //if (pieces[2].indexOf('x') >= 0) options += Pattern.EXTENDED;
        if (pieces[2].indexOf('i') >= 0) options += Pattern.CASE_INSENSITIVE;
        if (pieces[2].indexOf('m') >= 0) options += Pattern.MULTILINE;
        if (pieces[2].indexOf('s') >= 0) options += Pattern.DOTALL;
        
        int flags = 0;
        if (pieces[2].indexOf('r') >= 0) flags += FLAG_REPEAT;
        
        if (pieces[1].contains("\\r")) pieces[1] = pieces[1].replaceAll("\\\\r", "\r");
        if (pieces[1].contains("\\n")) pieces[1] = pieces[1].replaceAll("\\\\n", "\n");
        if (pieces[1].contains("\\t")) pieces[1] = pieces[1].replaceAll("\\\\t", "\t");

        return new PatternBean (Pattern.compile(pieces[0], options), pieces[1] , flags);
    }
    
    protected static PatternList getPatternsFromString(String pattern) throws MailetException {
        pattern = pattern.trim();
        if (pattern.length() < 2 && !pattern.startsWith("/") && !pattern.endsWith("/")) throw new MailetException("Invalid parameter value: " + PARAMETER_NAME_SUBJECT_PATTERN);
        pattern = pattern.substring(1, pattern.length() - 1);
        String[] patternArray = StringUtils.split(pattern, "/,/");
        
        PatternList patternList= new PatternList();
        for (String aPatternArray : patternArray) {
            PatternBean o = getPattern(aPatternArray);
            patternList.getPatterns().add(o.getPatterns());
            patternList.getSubstitutions().add(o.getSubstitutions());
            patternList.getFlags().add(o.getFlag());
        }
        
        return patternList;
    }

    protected static PatternList getPatternsFromStream(InputStream stream, String charset) throws MailetException, IOException {
        PatternList patternList= new PatternList();
        BufferedReader reader = new BufferedReader(charset != null ? new InputStreamReader(stream, charset) : new InputStreamReader(stream));
        //BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("q:\\correzioniout"), "utf-8"));
        
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0 && !line.startsWith("#")) {
                if (line.length() < 2 && !line.startsWith("/") && !line.endsWith("/")) throw new MailetException("Invalid expression: " + line);
                PatternBean o = getPattern(line.substring(1, line.length() - 1));
                patternList.getPatterns().add(o.getPatterns());
                patternList.getSubstitutions().add(o.getSubstitutions());
                patternList.getFlags().add(o.getFlag());
            }
        }
        reader.close();
        return patternList;
    }
    
    /**
     * @param filepar File path list (or resources if the path starts with #) comma separated
     */
    private PatternList getPatternsFromFileList(String filepar) throws MailetException, IOException {
        PatternList patternList= new PatternList();
        String[] files = filepar.split(",");
        for (int i = 0; i < files.length; i++) {
            files[i] = files[i].trim();
            if (debug > 0) log("Loading patterns from: " + files[i]);
            String charset = null;
            int pc = files[i].lastIndexOf('?');
            if (pc >= 0) {
                charset = files[i].substring(pc + 1);
                files[i] = files[i].substring(0, pc);
            }
            InputStream is = null;
            if (files[i].startsWith("#")) is = getClass().getResourceAsStream(files[i].substring(1));
            else {
                File f = new File(files[i]);
                if (f.isFile()) is = new FileInputStream(f);
            }
            if (is != null) {
                PatternList o = getPatternsFromStream(is, charset);
                patternList.getPatterns().addAll(o.getPatterns());
                patternList.getSubstitutions().addAll(o.getSubstitutions());
                patternList.getFlags().addAll(o.getFlags());
                is.close();
            }
        }
        return patternList;
    }
    
    protected static String applyPatterns(Pattern[] patterns, String[] substitutions, Integer[] pflags, String text, int debug, GenericMailet logOwner) {
        for (int i = 0; i < patterns.length; i ++) {
            int flags = pflags[i];
            boolean changed;
            do {
                changed = false;
                String replaced = patterns[i].matcher(text).replaceAll(substitutions[i]);
                if (!replaced.equals(text)) {
                    if (debug > 0) logOwner.log("Subject rule match: " + patterns[i].pattern());
                    text = replaced;
                    changed = true;
                }
            } while ((flags & FLAG_REPEAT) > 0 && changed);
        }
        
        return text;
    }
    

    public void init() throws MailetException {
        charset = getInitParameter(PARAMETER_NAME_CHARSET);
        debug = Integer.parseInt(getInitParameter("debug", "0"));
    }
    
    private ReplaceConfig initPatterns() throws MailetException {
        try {
            List<Pattern> bodyPatternsList = new ArrayList<Pattern>();
            List<String> bodySubstitutionsList = new ArrayList<String>();
            List<Integer> bodyFlagsList = new ArrayList<Integer>();
            List<Pattern> subjectPatternsList = new ArrayList<Pattern>();
            List<String> subjectSubstitutionsList = new ArrayList<String>();
            List<Integer> subjectFlagsList = new ArrayList<Integer>();

            String pattern = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERN);
            if (pattern != null) {
                PatternList o = getPatternsFromString(pattern);
                subjectPatternsList.addAll(o.getPatterns());
                subjectSubstitutionsList.addAll(o.getSubstitutions());
                subjectFlagsList.addAll(o.getFlags());
            }
            
            pattern = getInitParameter(PARAMETER_NAME_BODY_PATTERN);
            if (pattern != null) {
                PatternList o = getPatternsFromString(pattern);
                bodyPatternsList.addAll(o.getPatterns());
                bodySubstitutionsList.addAll(o.getSubstitutions());
                bodyFlagsList.addAll(o.getFlags());
            }
            
            String filepar = getInitParameter(PARAMETER_NAME_SUBJECT_PATTERNFILE);
            if (filepar != null) {
                PatternList o = getPatternsFromFileList(filepar);
                subjectPatternsList.addAll(o.getPatterns());
                subjectSubstitutionsList.addAll(o.getSubstitutions());
                subjectFlagsList.addAll(o.getFlags());
            }
        
            filepar = getInitParameter(PARAMETER_NAME_BODY_PATTERNFILE);
            if (filepar != null) {
                PatternList o = getPatternsFromFileList(filepar);
                bodyPatternsList.addAll(o.getPatterns());
                bodySubstitutionsList.addAll(o.getSubstitutions());
                bodyFlagsList.addAll(o.getFlags());
            }
            
            ReplaceConfig rConfig = new ReplaceConfig();
            rConfig.subjectPatterns = subjectPatternsList.toArray(new Pattern[subjectPatternsList.size()]);
            rConfig.subjectSubstitutions = subjectSubstitutionsList.toArray(new String[subjectSubstitutionsList.size()]);
            rConfig.subjectFlags = subjectFlagsList.toArray(new Integer[subjectFlagsList.size()]);
            rConfig.bodyPatterns = bodyPatternsList.toArray(new Pattern[bodyPatternsList.size()]);
            rConfig.bodySubstitutions = bodySubstitutionsList.toArray(new String[bodySubstitutionsList.size()]);
            rConfig.bodyFlags = bodyFlagsList.toArray(new Integer[bodyFlagsList.size()]);
            
            return rConfig;
            
        } catch (FileNotFoundException e) {
            throw new MailetException("Failed initialization", e);
            
        } catch (MailetException e) {
            throw new MailetException("Failed initialization", e);
            
        } catch (IOException e) {
            throw new MailetException("Failed initialization", e);
            
        }
    }

    public void service(Mail mail) throws MailetException {
        ReplaceConfig rConfig = initPatterns();
        
        try {
            boolean mod = false;
            boolean contentChanged = false;
            
            if (rConfig.subjectPatterns != null && rConfig.subjectPatterns.length > 0) {
                String subject = mail.getMessage().getSubject();
                if (subject == null) subject = "";
                subject = applyPatterns(rConfig.subjectPatterns, rConfig.subjectSubstitutions, rConfig.subjectFlags, subject, debug, this);
                if (charset != null) mail.getMessage().setSubject(subject, charset);
                else mail.getMessage().setSubject(subject);
                mod = true;
            }
            
            if (rConfig.bodyPatterns != null && rConfig.bodyPatterns.length > 0) {
                Object bodyObj = mail.getMessage().getContent();
                if (bodyObj == null) bodyObj = "";
                if (bodyObj instanceof String) {
                    String body = (String) bodyObj;
                    body = applyPatterns(rConfig.bodyPatterns, rConfig.bodySubstitutions, rConfig.bodyFlags, body, debug, this);
                    String contentType = mail.getMessage().getContentType();
                    if (charset != null) {
                        ContentType ct = new ContentType(contentType);
                        ct.setParameter("charset", charset);
                        contentType = ct.toString();
                    }
                    mail.getMessage().setContent(body, contentType);
                    mod = true;
                    contentChanged = true;
                }
            }
            
            if (charset != null && !contentChanged) {
                ContentType ct = new ContentType(mail.getMessage().getContentType());
                ct.setParameter("charset", charset);
                String contentType = mail.getMessage().getContentType();
                mail.getMessage().setContent(mail.getMessage().getContent(), contentType);
            }
            
            if (mod) mail.getMessage().saveChanges();
            
        } catch (MessagingException e) {
            throw new MailetException("Error in replace", e);
            
        } catch (IOException e) {
            throw new MailetException("Error in replace", e);
        }
    }

}
