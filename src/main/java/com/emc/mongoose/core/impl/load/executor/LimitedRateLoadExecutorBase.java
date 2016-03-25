package com.emc.mongoose.core.impl.load.executor;
// mongoose-common.jar
import com.emc.mongoose.common.conf.AppConfig;
// mongoose-core-api.jar
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.core.api.item.base.Item;
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.data.DataItem;
import com.emc.mongoose.core.api.item.base.ItemSrc;
import com.emc.mongoose.core.api.io.conf.IOConfig;
import com.emc.mongoose.core.api.io.task.IOTask;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 04.05.15.
 The extension of load executor which is able to sustain the rate (throughput, item/sec) not higher
 than the specified limit.
 */
public abstract class LimitedRateLoadExecutorBase<T extends Item>
extends LoadExecutorBase<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static int M = 1000000;
	//
	private final double tgtMicroDuration;
	//
	protected LimitedRateLoadExecutorBase(
		final AppConfig appConfig,
		final IOConfig<? extends DataItem, ? extends Container<? extends DataItem>> ioConfig,
		final String[] addrs, final int threadCount, final ItemSrc<T> itemSrc, final long maxCount,
		final float rateLimit
	) throws ClassCastException {
		super(appConfig, ioConfig, addrs, threadCount, itemSrc, maxCount);
		//
		if(rateLimit < 0) {
			throw new IllegalArgumentException("Frequency rate limit shouldn't be a negative value");
		}
		if(rateLimit > 0) {
			tgtMicroDuration = M * totalThreadCount / rateLimit;
		} else {
			tgtMicroDuration = 0;
		}
	}
	//
	@Override
	public <A extends IOTask<T>> Future<A> submitTask(final A task)
	throws RejectedExecutionException {
		// rate limit matching
		if(tgtMicroDuration > 0) {
			final double t = tgtMicroDuration - (M * totalThreadCount / lastStats.getSuccRateLast());
			if(t > 0) {
				try {
					TimeUnit.MICROSECONDS.sleep((long) t);
				} catch(final InterruptedException e) {
					throw new RejectedExecutionException(e);
				}
			}
		}
		//
		return submitTaskActually(task);
	}
	//
	protected abstract <A extends IOTask<T>> Future<A> submitTaskActually(final A ioTask)
	throws RejectedExecutionException;
}
