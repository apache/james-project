package org.apache.james.transport.util;

import com.google.common.base.Throwables;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Patterns {

    public static Pattern compilePatternUncheckedException(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException mpe) {
            throw Throwables.propagate(mpe);
        }
    }
}
