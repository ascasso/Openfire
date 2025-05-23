/*
 * Copyright (C) 2017-2025 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.admin;

import org.jivesoftware.util.ByteFormat;
import org.jivesoftware.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Utility functions that are exposed through a taglib.
 *
 * @author Guus der Kinderen
 */
public class JSTLFunctions
{
    /**
     * JSTL delegate for {@link String#replaceAll(String, String)}. The first argument is the value on which the
     * replacement has to occur. The other arguments are passed to {@link String#replaceAll(String, String)} directly.
     * @param string The string to search
     * @param regex the regular expression to which this string is to be matched
     * @param replacement the string to be substituted for each match
     * @return the updated string
     * @see String#replaceAll(String, String)
     */
    public static String replaceAll(String string, String regex, String replacement)
    {
        return string.replaceAll(regex, replacement);
    }

    /**
     * JSTL delegate for {@link String#split(String)}. The first argument is the value on which the replacement has to
     * occur. The other argument is used as the argument for the invocation of {@link String#split(String)}.
     * @param string The string to split
     * @param regex the delimiting regular expression
     * @return the array of strings computed by splitting this string around matches of the given regular expression
     * @see String#split(String)
     */
    public static String[] split(String string, String regex)
    {
        return string.split(regex);
    }

    /**
     * A formatter for formatting byte sizes. For example, formatting 12345 byes results in
     * "12.1 K" and 1234567 results in "1.18 MB".
     *
     * @param bytes the number of bytes
     * @return the number of bytes in terms of KB, MB, etc.
     * @see ByteFormat
     */
    public static String byteFormat( long bytes )
    {
        return new ByteFormat().format( bytes );
    }

    /**
     * Translates a string into {@code application/x-www-form-urlencoded} format using a specific encoding scheme. This
     * method uses the UTF-8 encoding scheme to obtain the bytes for unsafe characters.
     *
     * @param string the URL to encode
     * @return the encoded URL
     *
     * @see URLEncoder
     */
    public static String urlEncode( String string )
    {
        return URLEncoder.encode( string, StandardCharsets.UTF_8);
    }

    /**
     * Decodes a {@code application/x-www-form-urlencoded} string using the UTF-8 encoding scheme. The encoding is used
     * to determine what characters are represented by any consecutive sequences of the form "<i>{@code %xy}</i>".
     * @param string the String to encode
     * @return the encoded string
     * @see URLDecoder
     */
    public static String urlDecode( String string )
    {
        return URLDecoder.decode( string, StandardCharsets.UTF_8);
    }

    /**
     * This method takes a string which may contain HTML tags (ie, &lt;b&gt;,
     * &lt;table&gt;, etc) and converts the '&lt;' and '&gt;' characters to
     * their HTML escape sequences. It will also replace LF  with &lt;br&gt;.
     *
     * @param string the String to escape
     * @return the escaped string
     * @see StringUtils#escapeHTMLTags(String)
     */
    public static String escapeHTMLTags( String string )
    {
        return StringUtils.escapeHTMLTags( string );
    }
}
