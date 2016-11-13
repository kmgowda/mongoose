package com.emc.mongoose.load.generator;

import com.emc.mongoose.common.concurrent.DaemonBase;
import com.emc.mongoose.common.concurrent.Throttle;
import com.emc.mongoose.common.io.Output;
import com.emc.mongoose.common.io.ConstantStringInput;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.io.task.IoTaskBuilder;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.load.LoadGenerator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 Created by kurila on 11.07.16.
 */
public class BasicLoadGenerator<I extends Item, O extends IoTask<I>>
extends DaemonBase
implements LoadGenerator<I, O>, Output<I> {

	private static final Logger LOG = LogManager.getLogger();
	private static final int BATCH_SIZE = 0x1000;

	private volatile Throttle<LoadGenerator<I, O>> weightThrottle = null;
	private volatile Throttle<Object> rateThrottle = null;
	private volatile Output<O> ioTaskOutput;

	private final Input<I> itemInput;
	private final Input<String> dstPathInput;
	private final Thread worker;
	private final long countLimit;
	private final int maxItemQueueSize;
	private final boolean isShuffling = false;
	private final boolean isCircular;
	private final IoTaskBuilder<I, O> ioTaskBuilder;

	private long producedItemsCount = 0;

	@SuppressWarnings("unchecked")
	public BasicLoadGenerator(
		final Input<I> itemInput, final Input<String> dstPathInput,
		final IoTaskBuilder<I, O> ioTaskBuilder, final long countLimit, final int maxItemQueueSize,
		final boolean isCircular
	) throws UserShootHisFootException {

		this.itemInput = itemInput;
		this.dstPathInput = dstPathInput;
		this.ioTaskBuilder = ioTaskBuilder;
		this.countLimit = countLimit > 0 ? countLimit : Long.MAX_VALUE;
		this.maxItemQueueSize = maxItemQueueSize;
		this.isCircular = isCircular;

		final String ioStr = ioTaskBuilder.getIoType().toString();
		worker = new Thread(
			new GeneratorTask(),
			Character.toUpperCase(ioStr.charAt(0)) + ioStr.substring(1).toLowerCase() +
				(countLimit > 0 && countLimit < Long.MAX_VALUE ? Long.toString(countLimit) : "")
		);
		worker.setDaemon(true);
	}
	
	@Override
	public final void setWeightThrottle(final Throttle<LoadGenerator<I, O>> weightThrottle) {
		this.weightThrottle = weightThrottle;
	}

	@Override
	public final void setRateThrottle(final Throttle<Object> rateThrottle) {
		this.rateThrottle = rateThrottle;
	}

	@Override
	public final void setOutput(final Output<O> ioTaskOutput) {
		this.ioTaskOutput = ioTaskOutput;
	}

	private final class GeneratorTask
	implements Runnable {

		private final Random rnd = new Random();

		@Override
		public final void run() {

			if(ioTaskOutput == null) {
				LOG.warn(Markers.ERR, "No load I/O task output set, exiting");
			}

			if(itemInput == null) {
				LOG.warn(Markers.MSG, "No item source for the producing, exiting");
				return;
			}

			int n = 0, m = 0;
			long remaining;
			try {
				List<I> buff;
				while(!worker.isInterrupted()) {
					remaining = countLimit - producedItemsCount;
					if(remaining <= 0) {
						break;
					}
					try {
						buff = new ArrayList<>(BATCH_SIZE);
						n = itemInput.get(buff, BATCH_SIZE);
						if(n > remaining) {
							n = (int) remaining;
						}
						if(isShuffling) {
							Collections.shuffle(buff, rnd);
						}
						if(worker.isInterrupted()) {
							break;
						}
						if(n > 0) {
							for(m = 0; m < n && !worker.isInterrupted(); ) {
								m += put(buff, m, n);
							}
							producedItemsCount += n;
						} else {
							if(worker.isInterrupted()) {
								break;
							}
						}
						// CIRCULARITY FEATURE:
						// produce only <maxItemQueueSize> items in order to make it possible to
						// enqueue them infinitely
						if(isCircular && producedItemsCount >= maxItemQueueSize) {
							break;
						}
					} catch(
						final EOFException | ClosedByInterruptException | IllegalStateException |
							InterruptedIOException e
					) {
						break;
					} catch(final Exception e) {
						LogUtil.exception(
							LOG, Level.WARN, e, "Failed to read the data items, count = {}, " +
							"batch size = {}, batch offset = {}", producedItemsCount, n, m
						);
					}
				}
			} finally {
				LOG.debug(
					Markers.MSG, "{}: produced {} items from \"{}\" for the \"{}\"",
					Thread.currentThread().getName(), producedItemsCount, itemInput.toString(), this
				);
				try {
					shutdown();
				} catch(final IllegalStateException ignored) {
				}
			}
		}
	}

	@Override
	public final void put(final I item)
	throws IOException {

		final O nextIoTask = ioTaskBuilder.getInstance(item, dstPathInput.get());

		if(weightThrottle != null) {
			try {
				weightThrottle.getPassFor(this);
			} catch(final InterruptedException e) {
				throw new InterruptedIOException();
			}
		}

		if(rateThrottle != null) {
			try {
				rateThrottle.getPassFor(nextIoTask);
			} catch(final InterruptedException e) {
				throw new InterruptedIOException();
			}
		}

		ioTaskOutput.put(nextIoTask);
	}
	
	@Override
	public final int put(final List<I> buffer, final int from, final int to)
	throws IOException {
		int n = to - from;
		if(n > 0) {
			
			final List<O> ioTasks;
			if(dstPathInput == null) {
				ioTasks = ioTaskBuilder.getInstances(buffer, from, to);
			} else if(dstPathInput instanceof ConstantStringInput) {
				final String dstPath = dstPathInput.get();
				ioTasks = ioTaskBuilder.getInstances(buffer, dstPath, from, to);
			} else {
				final List<String> dstPaths = new ArrayList<>(n);
				dstPathInput.get(dstPaths, n);
				ioTasks = ioTaskBuilder.getInstances(buffer, dstPaths, from, to);
			}
			
			if(weightThrottle != null) {
				try {
					n = weightThrottle.getPassFor(this, n);
				} catch(final InterruptedException e) {
					throw new InterruptedIOException();
				}
			}

			if(rateThrottle != null) {
				try {
					rateThrottle.getPassFor(this, n);
				} catch(final InterruptedException e) {
					throw new InterruptedIOException();
				}
			}
			
			return ioTaskOutput.put(ioTasks, 0, n);
		} else {
			return 0;
		}
	}
	
	@Override
	public final int put(final List<I> buffer)
	throws IOException {
		return put(buffer, 0, buffer.size());
	}
	
	@Override
	public final Input<I> getInput()
	throws IOException {
		return itemInput;
	}

	@Override
	protected void doStart()
	throws IllegalStateException {
		worker.start();
	}

	@Override
	protected void doShutdown() {
		interrupt();
	}

	@Override
	protected void doInterrupt() {
		worker.interrupt();
	}

	@Override
	public boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptedException {
		timeUnit.timedJoin(worker, timeout);
		return true;
	}

	@Override
	protected void doClose()
	throws IOException {
		if(itemInput != null) {
			itemInput.close();
		}
	}
	
	@Override
	public final String toString() {
		return worker.getName();
	}
}
