package com.emc.mongoose;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.exception.InterruptRunException;
import com.emc.mongoose.metrics.MetricsManager;
import com.github.akurilov.confuse.Config;

import java.time.LocalDateTime;
import java.util.List;

/**
 @author veronika K. on 08.11.18 */
public interface Node {

	void run(final Config config, final List<Extension> extensions, final MetricsManager metricsMgr)
	throws InterruptRunException, InterruptedException;

	LocalDateTime startTime();

	String status();
}
