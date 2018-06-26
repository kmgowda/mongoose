package com.emc.mongoose.load.step.pipeline;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.item.io.IoType;
import com.emc.mongoose.load.step.client.LoadStepClient;
import com.emc.mongoose.load.step.client.LoadStepClientBase;
import com.emc.mongoose.logging.LogUtil;

import com.github.akurilov.commons.reflection.TypeUtil;
import com.github.akurilov.commons.system.SizeInBytes;
import static com.github.akurilov.commons.collection.TreeUtil.reduceForest;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;
import static com.github.akurilov.confuse.Config.deepToMap;

import org.apache.logging.log4j.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

public class PipelineLoadStepClient
extends LoadStepClientBase  {

	public PipelineLoadStepClient(
		final Config baseConfig, final List<Extension> extensions, final List<Map<String, Object>> stepConfigs
	) {
		super(baseConfig, extensions, stepConfigs);
	}

	@Override @SuppressWarnings("unchecked")
	protected <T extends LoadStepClient> T copyInstance(final Config config, final List<Map<String, Object>> contexts) {
		return (T) new PipelineLoadStepClient(config, extensions, contexts);
	}

	@Override
	protected void init()
	throws IllegalStateException {

		final String autoStepId = "pipeline_" + LogUtil.getDateTimeStamp();
		final Config stepConfig = config.configVal("load-step");
		if(stepConfig.boolVal("idAutoGenerated")) {
			stepConfig.val("id", autoStepId);
		}
		final int subStepCount = contexts.size();

		for(int originIndex = 0; originIndex < subStepCount; originIndex ++) {

			final Map<String, Object> mergedConfigTree = reduceForest(
				Arrays.asList(deepToMap(config), contexts.get(originIndex))
			);
			final Config subConfig;
			try {
				subConfig = new BasicConfig(config.pathSep(), config.schema(), mergedConfigTree);
			} catch(final InvalidValueTypeException | InvalidValuePathException e) {
				LogUtil.exception(Level.FATAL, e, "Scenario syntax error");
				throw new CancellationException();
			}
			final Config loadConfig = subConfig.configVal("load");
			final IoType ioType = IoType.valueOf(loadConfig.stringVal("type").toUpperCase());
			final int concurrency = loadConfig.intVal("step-limit-concurrency");
			final Config outputConfig = subConfig.configVal("output");
			final Config metricsConfig = outputConfig.configVal("metrics");
			final SizeInBytes itemDataSize;
			final Object itemDataSizeRaw = config.val("item-data-size");
			if(itemDataSizeRaw instanceof String) {
				itemDataSize = new SizeInBytes((String) itemDataSizeRaw);
			} else {
				itemDataSize = new SizeInBytes(TypeUtil.typeConvert(itemDataSizeRaw, long.class));
			}
			final boolean colorFlag = outputConfig.boolVal("color");

			initMetrics(originIndex, ioType, concurrency, metricsConfig, itemDataSize, colorFlag);
		}
	}

	@Override
	public String getTypeName() {
		return PipelineLoadStepExtension.TYPE;
	}
}
