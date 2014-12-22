package com.emc.mongoose.util.pool;
//
import com.emc.mongoose.util.logging.ExceptionHandler;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.util.concurrent.ConcurrentLinkedQueue;
/**
 Created by andrey on 09.06.14.
 A pool for any reusable objects(instances).
 Pooled objects should define "close()" method which will invoke "release" method putting the object(instance) back into a pool.
 Such instances pool may improve the performance in some cases.
 */
public final class InstancePool<T extends Reusable>
extends ConcurrentLinkedQueue<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final Class<T> itemCls;
	//
	public InstancePool(final Class<T> itemCls) {
		this.itemCls = itemCls;
	}
	//
	@SuppressWarnings({"unchecked", "ConstantConditions"})
	public final T take(final Object... args) {
		T item = poll();
		if(item == null) {
			try {
				item = itemCls.newInstance();
			} catch(final NullPointerException|InstantiationException|IllegalAccessException e) {
				ExceptionHandler.trace(LOG, Level.ERROR, e, "Reusable instantiation failure");
			}
		}
		return (T) item.reuse(args);
	}
	//
	public final void release(final T item) {
		add(item);
	}
}
