package com.emc.mongoose.web.ui.websockets;

import com.emc.mongoose.web.ui.logging.WebUIAppender;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
import com.emc.mongoose.web.ui.websockets.interfaces.WebSocketLogListener;
import com.google.gson.Gson;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;

/**
 * Created by gusakk on 10/24/14.
 */
@WebSocket
public final class LogSocket implements WebSocketLogListener {

	private Session session;
	private final static Gson gson = new Gson();
	private final static Logger LOG = LogManager.getLogger();

	@OnWebSocketClose
	public final void onClose(int statusCode, final String reason) {
		WebUIAppender.unregister(this);
		LOG.trace(Markers.MSG, "Web Socket closed. Reason: {}, StatusCode: {}", reason, statusCode);
	}

	@OnWebSocketError
	public final void onError(final Throwable t) {
		WebUIAppender.unregister(this);
		LOG.info(Markers.ERR, "Web Socket error. Message: {}", t.getMessage());
	}

	@OnWebSocketConnect
	public final void onConnect(final Session session) {
		LOG.trace(Markers.MSG, "Web Socket connection {}", session.getRemoteAddress());
		this.session = session;
		WebUIAppender.register(this);
		for (final LogEvent logEvent : WebUIAppender.getLogEventsList()) {
			sendMessage(logEvent);
		}
	}

	@OnWebSocketMessage
	public final void onMessage(final String message) {
		LOG.trace(Markers.MSG, "Message from Browser {}", message);
	}

	@Override
	public final synchronized void sendMessage(final LogEvent message) {
		try {
			if (session != null) {
				session.getRemote().sendString(gson.toJson(message));
			}
		} catch (final IOException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "WebSocket problem");
		}
	}


}
