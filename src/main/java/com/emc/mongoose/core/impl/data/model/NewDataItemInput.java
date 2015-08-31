package com.emc.mongoose.core.impl.data.model;
//
import com.emc.mongoose.core.api.data.DataItem;
import com.emc.mongoose.core.api.data.model.DataItemInput;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
/**
 Created by kurila on 24.07.15.
 */
public final class NewDataItemInput<T extends DataItem>
implements DataItemInput<T> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	private final Constructor<T> dataConstructor;
	private final long minObjSize, maxObjSize, sizeRange;
	private final float objSizeBias;
	private final ThreadLocalRandom thrLocalRnd = ThreadLocalRandom.current();
	//
	private String lastItemId = null;
	//
	public NewDataItemInput(
		final Class<T> dataCls, final long minObjSize, final long maxObjSize, final float objSizeBias
	) throws NoSuchMethodException, IllegalArgumentException {
		this.dataConstructor = dataCls.getConstructor(Long.class);
		this.minObjSize = minObjSize;
		this.maxObjSize = maxObjSize;
		this.objSizeBias = objSizeBias;
		sizeRange = maxObjSize - minObjSize;
		if(sizeRange < 0) {
			throw new IllegalArgumentException(
				"Min size " + minObjSize + " is greater than max size " + maxObjSize
			);
		}
	}
	//
	private long nextSize() {
		if(minObjSize == maxObjSize) {
			return minObjSize;
		} else {
			if(objSizeBias == 1) {
				return minObjSize + (long) (thrLocalRnd.nextDouble() * sizeRange);
			} else {
				return minObjSize + (long) Math.pow(thrLocalRnd.nextDouble(), objSizeBias) * sizeRange;
			}
		}
	}
	//
	@Override
	public final T read()
	throws IOException {
		try {
			return dataConstructor.newInstance(nextSize());
		} catch(final InstantiationException|IllegalAccessException|InvocationTargetException e) {
			throw new IOException(e);
		}
	}
	//
	@Override
	public int read(final List<T> buffer, final int maxCount)
	throws IOException {
		try {
			for(int i = 0; i < maxCount; i ++) {
				buffer.add(dataConstructor.newInstance(nextSize()));
			}
		} catch(final InstantiationException|IllegalAccessException|InvocationTargetException e) {
			throw new IOException(e);
		}
		return maxCount;
	}
	//

	public void setLastItemId(final String lastItemId) {
		this.lastItemId = lastItemId;
	}

	public String getLastItemId() {
		return lastItemId;
	}

	/**
	 * Does nothing
	 * @param countOfItems count of items which should be skipped from the beginning
	 * @throws IOException doesn't throw
	 */
	@Override
	public void skip(final long countOfItems)
	throws IOException {
	}
	//
	@Override
	public final void reset() {
	}
	//
	@Override
	public final void close() {
	}
	//
	@Override
	public final String toString() {
		return "newDataItemInput<" + dataConstructor.getDeclaringClass().getSimpleName() + ">";
	}
}
