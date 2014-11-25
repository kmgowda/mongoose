package com.emc.mongoose.base.load.impl;
//
import com.emc.mongoose.base.load.LoadExecutor;
import com.emc.mongoose.util.logging.ExceptionHandler;
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
Created by kurila on 23.10.14.
Register shutdown hook which should perform correct server-side shutdown even if user hits ^C
*/
public final class ShutDownHook
implements Runnable {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static Map<LoadExecutor, Thread> HOOKS_MAP = new ConcurrentHashMap<>();
	//
	private final LoadExecutor loadExecutor;
	private final String loadName;
	//
	private ShutDownHook(final LoadExecutor loadExecutor, final String loadName) {
		this.loadExecutor = loadExecutor;
		this.loadName = loadName;
	}
	//
	public static void add(final LoadExecutor loadExecutor) {
		//
		final String loadName;
		String ln = "";
		try {
			ln = loadExecutor.getName();
		} catch(final RemoteException e) {
			ExceptionHandler.trace(
				LOG, Level.WARN, e, "Failed to get the name of the remote load executor"
			);
		} finally {
			loadName = ln;
		}
		//
		final Thread hookThread = new Thread(
			new ShutDownHook(loadExecutor, loadName),
			String.format("shutDownHook<%s>", loadName)
		);
		try {
			Runtime.getRuntime().addShutdownHook(hookThread);
			HOOKS_MAP.put(loadExecutor, hookThread);
			LOG.debug(
				Markers.MSG, "Registered shutdown hook for the load executor \"{}\"", loadName
			);
		} catch(final SecurityException | IllegalArgumentException | IllegalStateException e) {
			ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to add the shutdown hook");
		}
	}
	//
	public static void del(final LoadExecutor loadExecutor) {
		if(HOOKS_MAP.containsKey(loadExecutor)) {
			try {
				Runtime.getRuntime().removeShutdownHook(HOOKS_MAP.get(loadExecutor));
				LOG.debug(Markers.MSG, "Shutdown hook for \"{}\" removed", loadExecutor);
			} catch(final SecurityException | IllegalArgumentException | IllegalStateException e) {
				ExceptionHandler.trace(LOG, Level.ERROR, e, "Failed to remove the shutdown hook");
			}
		} else {
			LOG.trace(Markers.ERR, "No shutdown hook registered for \"{}\"", loadExecutor);
		}
	}
	//
	@Override
	public final void run() {
		LOG.info(Markers.MSG, "Closing the load executor \"{}\"...", loadName);
		try {
			loadExecutor.close();
			LOG.info(Markers.MSG, "The load executor \"{}\"closed successfully", loadName);
		} catch(final Exception e) {
			ExceptionHandler.trace(
				LOG, Level.WARN, e,
				String.format("Failed to close the load executor \"%s\"", loadName)
			);
		}
	}
}
