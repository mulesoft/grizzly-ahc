/*
 * Copyright (c) 2014 AsyncHttpClient Project. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.ning.http.client.multipart;

import static com.ning.http.util.MiscUtils.isNonEmpty;
import static java.lang.Boolean.getBoolean;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import com.ning.http.client.Param;

public abstract class PartBase implements Part {
    
    public static final String GRIZZLY_ALLOW_UTF8 = "grizzly.request.part.headers.allowUtf8";

    private static Charset HEADERS_CHARSET = getHeadersCharset();

    /**
     * The name of the form field, part of the Content-Disposition header
     */
    private final String name;

    /**
     * The main part of the Content-Type header
     */
    private final String contentType;

    /**
     * The charset (part of Content-Type header)
     */
    private final Charset charset;

    /**
     * The Content-Transfer-Encoding header value.
     */
    private final String transferEncoding;

    /**
     * The Content-Id
     */
    private final String contentId;

    /**
     * The disposition type (part of Content-Disposition)
     */
    private String dispositionType;

    /**
     * Additional part headers
     */
    private List<Param> customHeaders;

    public PartBase(String name, String contentType, Charset charset, String contentId) {
        this(name, contentType, charset, contentId, null);
    }

    /**
     * Constructor.
     * 
     * @param name The name of the part, or <code>null</code>
     * @param contentType The content type, or <code>null</code>
     * @param charset The character encoding, or <code>null</code>
     * @param contentId The content id, or <code>null</code>
     * @param transferEncoding The transfer encoding, or <code>null</code>
     */
    public PartBase(String name, String contentType, Charset charset, String contentId, String transferEncoding) {
        this.name = name;
        this.contentType = contentType;
        this.charset = charset;
        this.contentId = contentId;
        this.transferEncoding = transferEncoding;
    }

    protected void visitStart(PartVisitor visitor, byte[] boundary) throws IOException {
        visitor.withBytes(EXTRA_BYTES);
        visitor.withBytes(boundary);
    }

    protected void visitDispositionHeader(PartVisitor visitor) throws IOException {
        visitor.withBytes(CRLF_BYTES);
        visitor.withBytes(CONTENT_DISPOSITION_BYTES);
        visitor.withBytes(getDispositionType() != null ? getDispositionType().getBytes(HEADERS_CHARSET) : FORM_DATA_DISPOSITION_TYPE_BYTES);
        if (getName() != null) {
            visitor.withBytes(NAME_BYTES);
            visitor.withByte(QUOTE_BYTE);
            visitor.withBytes(getName().getBytes(HEADERS_CHARSET));
            visitor.withByte(QUOTE_BYTE);
        }
    }

    protected void visitContentTypeHeader(PartVisitor visitor) throws IOException {
        String contentType = getContentType();
        if (contentType != null) {
            visitor.withBytes(CRLF_BYTES);
            visitor.withBytes(CONTENT_TYPE_BYTES);
            visitor.withBytes(contentType.getBytes(HEADERS_CHARSET));
            Charset charset = getCharset();
            if (charset != null) {
                visitor.withBytes(CHARSET_BYTES);
                visitor.withBytes(charset.name().getBytes(HEADERS_CHARSET));
            }
        }
    }

    protected void visitTransferEncodingHeader(PartVisitor visitor) throws IOException {
        String transferEncoding = getTransferEncoding();
        if (transferEncoding != null) {
            visitor.withBytes(CRLF_BYTES);
            visitor.withBytes(CONTENT_TRANSFER_ENCODING_BYTES);
            visitor.withBytes(transferEncoding.getBytes(HEADERS_CHARSET));
        }
    }

    protected void visitContentIdHeader(PartVisitor visitor) throws IOException {
        String contentId = getContentId();
        if (contentId != null) {
            visitor.withBytes(CRLF_BYTES);
            visitor.withBytes(CONTENT_ID_BYTES);
            visitor.withBytes(contentId.getBytes(US_ASCII));
        }
    }

    protected void visitCustomHeaders(PartVisitor visitor) throws IOException {
        if (isNonEmpty(customHeaders)) {
            for (Param param: customHeaders) {
                visitor.withBytes(CRLF_BYTES);
                visitor.withBytes(param.getName().getBytes(HEADERS_CHARSET));
                visitor.withBytes(param.getValue().getBytes(HEADERS_CHARSET));
            }
        }
    }

    protected void visitEndOfHeaders(PartVisitor visitor) throws IOException {
        visitor.withBytes(CRLF_BYTES);
        visitor.withBytes(CRLF_BYTES);
    }

    protected void visitEnd(PartVisitor visitor) throws IOException {
        visitor.withBytes(CRLF_BYTES);
    }

    protected abstract long getDataLength();

    protected abstract void sendData(OutputStream out) throws IOException;

    /**
     * Write all the data to the output stream. If you override this method make sure to override #length() as well
     * 
     * @param out
     *            The output stream
     * @param boundary
     *            the boundary
     * @throws IOException
     *             If an IO problem occurs.
     */
    public void write(OutputStream out, byte[] boundary) throws IOException {

        OutputStreamPartVisitor visitor = new OutputStreamPartVisitor(out);

        visitStart(visitor, boundary);
        visitDispositionHeader(visitor);
        visitContentTypeHeader(visitor);
        visitTransferEncodingHeader(visitor);
        visitContentIdHeader(visitor);
        visitCustomHeaders(visitor);
        visitEndOfHeaders(visitor);
        sendData(visitor.getOutputStream());
        visitEnd(visitor);
    }

    /**
     * Return the full length of all the data. If you override this method make sure to override #send(OutputStream) as well
     * 
     * @return long The length.
     */
    public long length(byte[] boundary) {

        long dataLength = getDataLength();
        try {

            if (dataLength < 0L) {
                return -1L;
            } else {
                CounterPartVisitor visitor = new CounterPartVisitor();
                visitStart(visitor, boundary);
                visitDispositionHeader(visitor);
                visitContentTypeHeader(visitor);
                visitTransferEncodingHeader(visitor);
                visitContentIdHeader(visitor);
                visitCustomHeaders(visitor);
                visitEndOfHeaders(visitor);
                visitEnd(visitor);
                return dataLength + visitor.getCount();
            }
        } catch (IOException e) {
            // can't happen
            throw new RuntimeException("IOException while computing length, WTF", e);
        }
    }

    public String toString() {
        return new StringBuilder()//
                .append(getClass().getSimpleName())//
                .append(" name=").append(getName())//
                .append(" contentType=").append(getContentType())//
                .append(" charset=").append(getCharset())//
                .append(" tranferEncoding=").append(getTransferEncoding())//
                .append(" contentId=").append(getContentId())//
                .append(" dispositionType=").append(getDispositionType())//
                .toString();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getContentType() {
        return this.contentType;
    }

    @Override
    public Charset getCharset() {
        return this.charset;
    }

    @Override
    public String getTransferEncoding() {
        return transferEncoding;
    }

    @Override
    public String getContentId() {
        return contentId;
    }

    @Override
    public String getDispositionType() {
        return dispositionType;
    }

    public void setDispositionType(String dispositionType) {
        this.dispositionType = dispositionType;
    }

    public void addCustomHeader(String name, String value) {
        if (customHeaders == null) {
            customHeaders = new ArrayList<Param>(2);
        }
        customHeaders.add(new Param(name, value));
    }

    public void setCustomHeaders(List<Param> customHeaders) {
        this.customHeaders = customHeaders;
    }
    
    private static Charset getHeadersCharset() {
        if (getBoolean(GRIZZLY_ALLOW_UTF8)) {
            return UTF_8;
        } else {
            return US_ASCII;
        }
    }
}
