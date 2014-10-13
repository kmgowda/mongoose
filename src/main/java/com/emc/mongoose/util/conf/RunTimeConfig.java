package com.emc.mongoose.util.conf;
//
import com.emc.mongoose.util.logging.Markers;
//
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.SystemConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 Created by kurila on 28.05.14.
 A shared runtime configuration.
 */
public final class RunTimeConfig
extends BaseConfiguration
implements Externalizable {
	//
	private final static Logger LOG = LogManager.getLogger();
	public final static String LIST_SEP = ",", KEY_VERSION = "run.version";
	private final static Map<String, String[]> MAP_OVERRIDE = new HashMap<>();
	static {
		MAP_OVERRIDE.put(
			"data.size",
			new String[] {
				"data.size.min",
				"data.size.max"
			}
		);
		MAP_OVERRIDE.put(
			"load.threads",
			new String[] {
				"load.create.threads",
				"load.read.threads",
				"load.update.threads",
				"load.delete.threads"
			}
		);
		MAP_OVERRIDE.put(
			"remote.drivers",
			new String[] {
				"remote.servers"
			}
		);
	}
	//
	private final static String
		SIZE_UNITS = "kmgtpe",
		FMT_MSG_INVALID_SIZE = "The string \"%s\" doesn't match the pattern: \"%s\"";
	private final static Pattern PATTERN_SIZE = Pattern.compile("(\\d+)(["+SIZE_UNITS+"]?)b?");
	//
	public long getSizeBytes(final String key) {
		final String value = getString(key).toLowerCase(), unit;
		final Matcher matcher = PATTERN_SIZE.matcher(value);
		long size, degree;
		if(matcher.matches() && matcher.groupCount() > 0 && matcher.groupCount() < 3) {
			size = Long.valueOf(matcher.group(1), 10);
			unit = matcher.group(2);
			if(unit.length() == 0 && value.indexOf('b') > 0) {
				degree = 0;
			} else if(unit.length() == 1) {
				degree = SIZE_UNITS.indexOf(matcher.group(2)) + 1;
			} else {
				throw new IllegalArgumentException(
					String.format(FMT_MSG_INVALID_SIZE, key, PATTERN_SIZE)
				);
			}
		} else {
			throw new IllegalArgumentException(
				String.format(FMT_MSG_INVALID_SIZE, key, PATTERN_SIZE)
			);
		}
		size *= 1L << 10 * degree;
		LOG.trace(Markers.MSG, "\"{}\" is {} bytes", value, size);
		return size;
	}
	//
	public static String formatSize(final long v) {
		if(v < 1024) {
			return v + "B";
		}
		final int z = (63 - Long.numberOfLeadingZeros(v)) / 10;
		final double x = (double) v / (1L << (z * 10));
		return String.format(
			Locale.ROOT,
			x < 10 ? "%.3f%sb" : x < 100 ? "%.2f%sb" : "%.1f%sb",
			x, z > 0 ? SIZE_UNITS.charAt(z - 1) : ""
		).toUpperCase();
	}
	//
	public final void set(final String key, final String value) {
		setProperty(key, value);
		System.setProperty(key, value);
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	public final int getRunReqTimeOutMilliSec() {
		return getInt("run.request.timeout.millisec");
	}
	//
	public final int getRunRetryDelayMilliSec() {
		return getInt("run.retry.delay.millisec");
	}
	//
	public final int getRunRetryCountMax() {
		return getInt("run.retry.count.max");
	}
	//
	public final boolean getRunRequestRetries() {
		return getBoolean("run.request.retries");
	}
	//
	public final String getStorageApi() {
		return getString("storage.api");
	}
	//
	public final int getApiPort(final String api) {
		return getInt("api." + api + ".port");
	}
	//
	public final String getAuthId() {
		return getString("auth.id");
	}
	//
	public final String getAuthSecret() {
		return getString("auth.secret");
	}
	//
	public final long getDataPageSize() {
		return getSizeBytes("data.page.size");
	}
	//
	public final int getRemoteMonitorPort() {
		return getInt("remote.monitor.port");
	}
	//
	public final int getRunMetricsPeriodSec() {
		return getInt("run.metrics.period.sec");
	}
	//
	public final int getRunRequestQueueFactor() {
		return getInt("run.request.queue.factor");
	}
	//
	public final String getHttpContentType() {
		return getString("http.content.type");
	}
	//
	public final boolean getHttpContentRepeatable() {
		return getBoolean("http.content.repeateable");
	}
	//
	public final boolean getHttpContentChunked() {
		return getBoolean("http.content.chunked");
	}
	//
	public final boolean getReadVerifyContent() {
		return getBoolean("load.read.verify.content");
	}
	//
	public final String getStorageProto() {
		return getString("storage.scheme");
	}
	//
	public final String getDataNameSpace() {
		return getString("data.namespace");
	}
	//
	public final String getHttpSignMethod() {
		return getString("http.sign.method");
	}
	//
	public final String getRunName() {
		return getString("run.name");
	}
	//
	public final String getRunVersion() {
		return getString("run.version");
	}
	//
	public final long getDataCount() {
		return getLong("data.count");
	}
	//
	public final String[] getStorageAddrs() {
		return getStringArray("storage.addrs");
	}
	//
	public final String[] getRemoteServers() {
		return getStringArray("remote.servers");
	}
	//
	public final String getDataSrcFPath() {
		return getString("data.src.fpath");
	}
	//
	public final String getRunScenarioLang() {
		return getString("run.scenario.lang");
	}
	//
	public final String getRunScenarioName() {
		return getString("run.scenario.name");
	}
	//
	public final String getRunScenarioDir() {
		return getString("run.scenario.dir");
	}
	//
	public final String getRunTime() {
		return getString("run.time");
	}
	//
	public final String getRunMode() {
		return getString("run.mode");
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	@Override
	public final synchronized void writeExternal(final ObjectOutput out)
	throws IOException {
		LOG.debug(Markers.MSG, "Going to upload properties to a server");
		String nextPropName;
		Object nextPropValue;
		final HashMap<String, String> propsMap = new HashMap<>();
		for(final Iterator<String> i = getKeys(); i.hasNext();) {
			nextPropName = i.next();
			nextPropValue = getProperty(nextPropName);
			LOG.trace(Markers.MSG, "Write property: \"{}\" = \"{}\"", nextPropName, nextPropValue);
			if(List.class.isInstance(nextPropValue)) {
				propsMap.put(
					nextPropName,
					StringUtils.join(List.class.cast(nextPropValue), LIST_SEP)
				);
			} else if(String.class.isInstance(nextPropValue)) {
				propsMap.put(nextPropName, String.class.cast(nextPropValue));
			} else if(nextPropValue==null) {
				LOG.warn(Markers.ERR, "Property \"{}\" is null");
			} else {
				LOG.error(
					Markers.ERR, "Unexpected type \"{}\" for property \"{}\"",
					nextPropValue.getClass().getCanonicalName(), nextPropName
				);
			}
		}
		//
		LOG.trace(Markers.MSG, "Sending configuration: {}", propsMap);
		//
		out.writeObject(propsMap);
		LOG.debug(Markers.MSG, "Uploaded the properties from client side");
	}
	//
	@Override @SuppressWarnings("unchecked")
	public final synchronized void readExternal(final ObjectInput in)
	throws IOException, ClassNotFoundException {
		LOG.debug(Markers.MSG, "Going to fetch the properties from client side");
		final HashMap<String, String> propsMap = HashMap.class.cast(in.readObject());
		LOG.trace(Markers.MSG, "Got the properties from client side: {}", propsMap);
		//
		final String
			serverVersion = getString(KEY_VERSION),
			clientVersion = propsMap.get(KEY_VERSION);
		if(serverVersion.equals(clientVersion)) {
			// put the properties into the System
			Object nextPropValue;
			for(final String nextPropName: propsMap.keySet()) {
				nextPropValue = propsMap.get(nextPropName);
				LOG.trace(Markers.MSG, "Read property: \"{}\" = \"{}\"", nextPropName, nextPropValue);
				if(List.class.isInstance(nextPropValue)) {
					setProperty(
						nextPropName,
						StringUtils.join(List.class.cast(nextPropValue), LIST_SEP)
					);
				} else if(String.class.isInstance(nextPropValue)) {
					setProperty(nextPropName, String.class.cast(nextPropValue));
				} else if(nextPropValue==null) {
					LOG.warn(Markers.ERR, "Property \"{}\" is null", nextPropName);
				} else {
					LOG.error(
						Markers.ERR, "Unexpected type \"{}\" for property \"{}\"",
						nextPropValue.getClass().getCanonicalName(), nextPropName
					);
				}
			}
		} else {
			LOG.fatal(
				Markers.ERR, "Version mismatch, server: {}, client: {}",
				serverVersion, clientVersion
			);
			throw new IOException("Version mismatch");
		}
	}
	////////////////////////////////////////////////////////////////////////////////////////////////
	public void loadPropsFromDir(final Path propsDir) {
		DirectoryLoader.loadPropsFromDir(propsDir, this);
	}
	//
	public void loadSysProps() {
		final SystemConfiguration sysProps = new SystemConfiguration();
		String key, keys2override[];
		Object sharedValue;
		for(final Iterator<String> keyIter=sysProps.getKeys(); keyIter.hasNext();) {
			key = keyIter.next();
			LOG.trace(
				Markers.MSG, "System property: \"{}\": \"{}\" -> \"{}\"",
				key, getProperty(key), sysProps.getProperty(key)
			);
			keys2override = MAP_OVERRIDE.get(key);
			sharedValue = sysProps.getProperty(key);
			if(keys2override==null) {
				setProperty(key, sharedValue);
			} else {
				for(final String key2override: keys2override) {
					setProperty(key2override, sharedValue);
				}
			}
		}
	}
	//
}
