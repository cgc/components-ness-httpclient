package io.trumpet.httpclient.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class GenericWritingContentHandler extends AbstractHandler
{
    private String content = "";
    private String contentType = "text/html";

    private String postData = "";

    private String method = null;

    @Override
    public void handle(final String target,
        final Request request,
        final HttpServletRequest httpRequest,
        final HttpServletResponse httpResponse)
    throws IOException, ServletException
    {
        method = request.getMethod();

        InputStream inputStream = request.getInputStream();

        postData = IOUtils.toString(inputStream);

        httpResponse.setContentType(contentType);
        httpResponse.setStatus(HttpServletResponse.SC_OK);

        request.setHandled(true);

        final PrintWriter writer = httpResponse.getWriter();
        writer.print(content);
        writer.flush();
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(final String content)
    {
        this.content = content;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(final String contentType)
    {
        this.contentType = contentType;
    }

    public String getPostData()
    {
        return postData;
    }

    public String getMethod()
    {
        return method;
    }
}
