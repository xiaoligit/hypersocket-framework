package com.hypersocket.server.handlers.impl;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.hypersocket.server.handlers.HttpResponseProcessor;

public interface ContentHandler {

	public abstract boolean handlesRequest(HttpServletRequest request);

	public abstract void handleHttpRequest(HttpServletRequest request,
			HttpServletResponse response,
			HttpResponseProcessor responseProcessor) throws IOException;

	public abstract String getResourceName();

	public abstract InputStream getResourceStream(String path)
			throws FileNotFoundException;

	public abstract long getResourceLength(String path)
			throws FileNotFoundException;

	public abstract long getLastModified(String path)
			throws FileNotFoundException;

	public abstract int getResourceStatus(String path);

	public abstract void addAlias(String alias, String path);

	void addFilter(ContentFilter filter);

}