package io.trumpet.httpclient.response;

import io.trumpet.httpclient.HttpClientResponse;
import io.trumpet.httpclient.HttpClientResponseHandler;
import io.trumpet.httpclient.io.SizeExceededException;
import io.trumpet.httpclient.io.SizeLimitingInputStream;
import com.nesscomputing.logging.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.StringUtils;


/**
 * A generic content response handler for the Http Client. It handles all cases of redirect, compressed responses etc.
 */
public class ContentResponseHandler<T> implements HttpClientResponseHandler<T>
{
    private static final Log LOG = Log.findLog();

    private final ContentConverter<T> contentConverter;
    private final int maxBodyLength;
    private final boolean allowRedirect;

    public static <CC> ContentResponseHandler<CC> forConverter(final ContentConverter<CC> contentConverter)
    {
        return new ContentResponseHandler<CC>(contentConverter);
    }

    /**
     * Creates a new ContentResponseHandler. It accepts unlimited data and will not follow redirects.
     *
     * @param contentConverter The content converter to use to convert the response into the reply object.
     */
    public ContentResponseHandler(final ContentConverter<T> contentConverter)
    {
        this(contentConverter, -1, false);
    }

    /**
     * Creates a new ContentResponseHandler. It will not follow redirects.
     *
     * @param contentConverter The content converter to use to convert the response into the reply object.
     * @param maxBodyLength The maximum number of bytes to read from the server. -1 means 'unlimited'.
     *
     * @throws SizeExceededException When the body length is bigger than maxBodyLength.
     */
    public ContentResponseHandler(final ContentConverter<T> contentConverter, final int maxBodyLength)
    {
        this(contentConverter, maxBodyLength, false);
    }

    /**
     * Creates a new ContentResponseHandler.
     *
     * @param contentConverter The content converter to use to convert the response into the reply object.
     * @param maxBodyLength The maximum number of bytes to read from the server. -1 means 'unlimited'.
     * @param allowRedirect If true, the handler will throw a {@link RedirectedException} to signal redirection to the caller.
     *
     * @throws SizeExceededException When the body length is bigger than maxBodyLength.
     * @throws RedirectedException When the server returned a 3xx return code.
     */
    public ContentResponseHandler(final ContentConverter<T> contentConverter, final int maxBodyLength, final boolean allowRedirect)
    {
        this.contentConverter = contentConverter;
        this.maxBodyLength = maxBodyLength;
        this.allowRedirect = allowRedirect;
    }

    /**
     * Processes the client response.
     */
    @Override
    public T handle(final HttpClientResponse response) throws IOException
    {
        if(allowRedirect && response.isRedirected()) {
            LOG.debug("Redirecting based on '%d' response code", response.getStatusCode());
            throw new RedirectedException(response);
        } else {
            // Find the response stream - the error stream may be valid in cases
            // where the input stream is not.
            InputStream is = null;
            try {
                is = response.getResponseBodyAsStream();
            }
            catch (IOException e) {
                LOG.warnDebug(e, "Could not locate response body stream");
                // normal for 401, 403 and 404 responses, for example...
            }

            if (is == null) {
                // Fall back to zero length response.
                is = new NullInputStream(0);
            }

            try {
                final Long contentLength = response.getContentLength();

                if (maxBodyLength > 0) {
                    if (contentLength != null && contentLength > maxBodyLength) {
                        throw new SizeExceededException("Content-Length: " + String.valueOf(contentLength));
                    }

                    LOG.debug("Limiting stream length to '%d'", maxBodyLength);
                    is = new SizeLimitingInputStream(is, maxBodyLength);
                }

                final String encoding = StringUtils.trimToEmpty(response.getHeader("Content-Encoding"));

                if (StringUtils.equalsIgnoreCase(encoding, "gzip") || StringUtils.equalsIgnoreCase(encoding, "x-gzip")) {
                    LOG.debug("Found GZIP stream");
                    is = new GZIPInputStream(is);
                }
                else if (StringUtils.equalsIgnoreCase(encoding, "deflate")) {
                    LOG.debug("Found deflate stream");
                    final Inflater inflater = new Inflater(true);
                    is = new InflaterInputStream(is, inflater);
                }

                return contentConverter.convert(response, is);
            }
            catch (HttpResponseException hre) {
                throw hre;
            }
            catch (IOException ioe) {
                return contentConverter.handleError(response, ioe);
            }
        }
    }
}