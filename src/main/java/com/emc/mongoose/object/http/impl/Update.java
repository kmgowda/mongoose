package com.emc.mongoose.object.http.impl;
//
import com.emc.mongoose.logging.ExceptionHandler;
import com.emc.mongoose.logging.Markers;
import com.emc.mongoose.object.http.WSLoadExecutor;
import com.emc.mongoose.object.http.data.WSObject;
import com.emc.mongoose.object.http.api.WSRequestConfig;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
/**
 Created by kurila on 06.05.14.
 */
public class Update
extends WSLoadExecutor {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final int updatesPerObject;
	//
	public Update(
		final String[] addrs, final WSRequestConfig sharedReqConf, final long maxCount,
		final int threadsPerNode, final String listFile, final int updatesPerObject
	) {
		super(addrs, sharedReqConf, maxCount, threadsPerNode, listFile);
		this.updatesPerObject = updatesPerObject;
	}
	//
	@Override
	public final void submit(final WSObject wsObject) {
		if(wsObject!=null) {
			try {
				wsObject.updateRandomRanges(updatesPerObject);
			} catch(final Exception e) {
				ExceptionHandler.trace(LOG, Level.WARN, e, "Failed to create modified ranges");
			}
			if(LOG.isTraceEnabled(Markers.MSG)) {
				LOG.trace(
					Markers.MSG, "Modified {}/{} ranges for object \"{}\"",
					wsObject.getPendingUpdatesCount(), updatesPerObject,
					Long.toHexString(wsObject.getId())
				);
			}
		}
		super.submit(wsObject);
	}
}
