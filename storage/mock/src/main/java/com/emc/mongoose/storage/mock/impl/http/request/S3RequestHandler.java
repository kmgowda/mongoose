package com.emc.mongoose.storage.mock.impl.http.request;

import com.emc.mongoose.storage.mock.api.MutableDataItemMock;
import com.emc.mongoose.storage.mock.api.StorageMock;
import com.emc.mongoose.storage.mock.api.StorageMockClient;
import com.emc.mongoose.storage.mock.api.exception.ContainerMockException;
import com.emc.mongoose.storage.mock.api.exception.ContainerMockNotFoundException;
import static com.emc.mongoose.ui.config.Config.ItemConfig.NamingConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig.LimitConfig;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import static com.emc.mongoose.storage.mock.impl.http.request.XmlShortcuts.appendElement;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 Created on 12.07.16.
 */
public class S3RequestHandler<T extends MutableDataItemMock>
extends RequestHandlerBase<T> {

	private static final Logger LOG = LogManager.getLogger();
	private static final DocumentBuilder DOM_BUILDER;
	private static final TransformerFactory TRANSFORMER_FACTORY = TransformerFactory.newInstance();
	private static final String S3_NAMESPACE_URI = "http://s3.amazonaws.com/doc/2006-03-01/";

	static {
		try {
			DOM_BUILDER = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		} catch (final ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
	}

	public S3RequestHandler(
		final LimitConfig limitConfig, final NamingConfig namingConfig,
		final StorageMock<T> localStorage, final StorageMockClient<T> remoteStorage
	) throws RemoteException {
		super(limitConfig, namingConfig, localStorage, remoteStorage);
	}

	@Override
	protected void doHandle(
		final String uri, final HttpMethod method, final long size, final ChannelHandlerContext ctx
	) {
		final int uriPathEnd = uri.indexOf('?');
		final String uriPath;
		final Map<String, String> queryParams;
		if(uriPathEnd > 0) {
			uriPath = uri.substring(0, uriPathEnd);
			final String queryParamsStr = uri.substring(uriPathEnd + 1);
			final String queryParamPairs[] = queryParamsStr.split("&");
			queryParams = new HashMap<>();
			String nextKeyValuePair[];
			for(final String queryParamPair : queryParamPairs) {
				nextKeyValuePair = queryParamPair.split("=");
				if(nextKeyValuePair.length == 2) {
					queryParams.put(nextKeyValuePair[0], nextKeyValuePair[1]);
				} else if(nextKeyValuePair.length == 1){
					queryParams.put(nextKeyValuePair[0], "");
				}
			}
		} else {
			uriPath = uri;
			queryParams = null;
		}
		final String uriPathParts[] = uriPath.split("/");
		final String containerName = uriPathParts[1];
		final String objectId = uriPathParts.length > 2 ? uriPathParts[2] : null;
		final Channel channel = ctx.channel();
		channel.attr(ATTR_KEY_CTX_WRITE_FLAG).set(true);
		if(containerName != null) {
			if(objectId != null && queryParams != null && queryParams.containsKey("uploads")) {
				handleMpuInitRequest(containerName, objectId, ctx);
			} else {
				handleItemRequest(uri, method, containerName, objectId, size, ctx);
			}
		} else {
			setHttpResponseStatusInContext(ctx, BAD_REQUEST);
		}
		if(channel.attr(ATTR_KEY_CTX_WRITE_FLAG).get()) {
			writeEmptyResponse(ctx);
		}
	}
	
	private void handleMpuInitRequest(
		final String containerName, final String objectId, final ChannelHandlerContext ctx
	) {
		final Document xml = DOM_BUILDER.newDocument();
		final Element rootElem = xml.createElementNS(
			S3_NAMESPACE_URI, "InitiateMultipartUploadResult"
		);
		xml.appendChild(rootElem);
		appendElement(xml, rootElem, "Bucket", containerName);
		appendElement(xml, rootElem, "Key", objectId);
		appendElement(xml, rootElem, "UploadId", generateBase64Id(0x10));
		
		final ByteArrayOutputStream stream = new ByteArrayOutputStream();
		final StreamResult streamResult = new StreamResult(stream);
		try {
			TRANSFORMER_FACTORY.newTransformer().transform(new DOMSource(xml), streamResult);
		} catch (final TransformerException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to build bucket XML listing");
			return;
		}
		final byte[] content = stream.toByteArray();
		ctx.channel().attr(ATTR_KEY_CTX_WRITE_FLAG).set(false);
		final FullHttpResponse response = new DefaultFullHttpResponse(
			HTTP_1_1, OK, Unpooled.copiedBuffer(content)
		);
		response.headers().set(CONTENT_TYPE, "application/xml");
		HttpUtil.setContentLength(response, content.length);
		ctx.write(response);
	}

	private static final String MAX_COUNT_KEY = "max-keys";

	@Override
	protected final void handleContainerList(
		final String name, final QueryStringDecoder queryStringDecoder,
		final ChannelHandlerContext ctx
	) {
		int maxCount = DEFAULT_PAGE_SIZE;
		String marker = null;
		final Map<String, List<String>> parameters = queryStringDecoder.parameters();
		if(parameters.containsKey(MAX_COUNT_KEY)) {
			maxCount = Integer.parseInt(parameters.get(MAX_COUNT_KEY).get(0));
		} else {
			LOG.warn(
				Markers.ERR, "Failed to parse max keys argument value in the URI {}",
				queryStringDecoder.uri()
			);
		}
		if(parameters.containsKey(MARKER_KEY)) {
			marker = parameters.get(MARKER_KEY).get(0);
		}
		final List<T> buffer = new ArrayList<>(maxCount);
		T lastObject;
		try {
			lastObject = listContainer(name, marker, buffer, maxCount);
			LOG.trace(
				Markers.MSG, "Bucket \"{}\": generated list of {} objects, last one is \"{}\"",
				name, buffer.size(), lastObject
			);
		} catch(final ContainerMockNotFoundException e) {
			setHttpResponseStatusInContext(ctx, NOT_FOUND);
			return;
		} catch(final ContainerMockException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			return;
		}
		final Document xml = DOM_BUILDER.newDocument();
		final Element rootElem = xml.createElementNS(S3_NAMESPACE_URI, "ListBucketResult");
		xml.appendChild(rootElem);
		appendElement(xml, rootElem, "Name", name);
		appendElement(xml, rootElem, "IsTruncated", Boolean.toString(lastObject != null));
		appendElement(xml, rootElem, "Prefix");
		appendElement(xml, rootElem, "MaxKeys", Integer.toString(buffer.size()));
		for(final T object: buffer) {
			final Element elem = xml.createElement("Contents");
			appendElement(xml, elem, "Key", object.getName());
			try {
				appendElement(xml, elem, "Size", Long.toString(object.size()));
			} catch(final IOException ignored) {
			}
			appendElement(rootElem, elem);
		}
		final ByteArrayOutputStream stream = new ByteArrayOutputStream();
		final StreamResult streamResult = new StreamResult(stream);
		try {
			TRANSFORMER_FACTORY.newTransformer().transform(new DOMSource(xml), streamResult);
		} catch (final TransformerException e) {
			setHttpResponseStatusInContext(ctx, INTERNAL_SERVER_ERROR);
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to build bucket XML listing");
			return;
		}
		final byte[] content = stream.toByteArray();
		ctx.channel().attr(ATTR_KEY_CTX_WRITE_FLAG).set(false);
		final FullHttpResponse response = new DefaultFullHttpResponse(
			HTTP_1_1, OK, Unpooled.copiedBuffer(content)
		);
		response.headers().set(CONTENT_TYPE, "application/xml");
		HttpUtil.setContentLength(response, content.length);
		ctx.write(response);
	}


}
