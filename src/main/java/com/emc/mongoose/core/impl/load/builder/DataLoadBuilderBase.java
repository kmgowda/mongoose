package com.emc.mongoose.core.impl.load.builder;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.DataRangesConfig;
import com.emc.mongoose.common.conf.SizeInBytes;
import com.emc.mongoose.common.conf.enums.ItemNamingType;
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
//
import com.emc.mongoose.core.api.item.data.DataItem;
import com.emc.mongoose.core.api.item.data.FileDataItemInput;
import com.emc.mongoose.core.api.io.conf.IoConfig;
import com.emc.mongoose.core.api.load.builder.DataLoadBuilder;
import com.emc.mongoose.core.api.load.builder.LoadBuilder;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
//
import com.emc.mongoose.core.impl.item.base.BasicItemNameInput;
import com.emc.mongoose.core.impl.item.base.ItemCsvFileOutput;
import com.emc.mongoose.core.impl.item.base.CsvFileItemInput;
import com.emc.mongoose.core.impl.item.data.NewDataItemInput;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Paths;
import java.rmi.RemoteException;
/**
 Created by kurila on 20.10.15.
 */
public abstract class DataLoadBuilderBase<T extends DataItem, U extends LoadExecutor<T>>
extends LoadBuilderBase<T, U>
implements DataLoadBuilder<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	protected SizeInBytes sizeConfig;
	protected DataRangesConfig rangesConfig;
	//
	public DataLoadBuilderBase(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
	}
	//
	@Override
	public DataLoadBuilderBase<T, U> clone()
	throws CloneNotSupportedException {
		final DataLoadBuilderBase<T, U> lb = (DataLoadBuilderBase<T, U>) super.clone();
		lb.sizeConfig = sizeConfig;
		lb.rangesConfig = rangesConfig;
		return lb;
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected Input<T> getNewItemInput()
	throws NoSuchMethodException {
		final ItemNamingType namingType = appConfig.getItemNamingType();
		final BasicItemNameInput bing = new BasicItemNameInput(
			namingType, appConfig.getItemNamingPrefix(), appConfig.getItemNamingLength(),
			appConfig.getItemNamingRadix(), appConfig.getItemNamingOffset()
		);
		return new NewDataItemInput<>(
			(Class<T>) ioConfig.getItemClass(), bing, ioConfig.getContentSource(), sizeConfig
		);
	}
	//
	@SuppressWarnings("unchecked")
	private Input<T> getContainerItemInput()
	throws CloneNotSupportedException {
		return (Input<T>) ((IoConfig) ioConfig.clone()).getContainerListInput(
			maxCount, storageNodeAddrs == null ? null : storageNodeAddrs[0]
		);
	}
	//
	@Override
	protected Input<T> getDefaultItemInput() {
		try {
			if(flagUseNoneItemSrc) {
				return null;
			} else if(flagUseContainerItemSrc && flagUseNewItemSrc) {
				if(LoadType.WRITE.equals(ioConfig.getLoadType())) {
					return getNewItemInput();
				} else {
					return getContainerItemInput();
				}
			} else if(flagUseNewItemSrc) {
				return getNewItemInput();
			} else if(flagUseContainerItemSrc) {
				return getContainerItemInput();
			}
		} catch(final NoSuchMethodException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to build the new data items source");
		} catch(final CloneNotSupportedException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Failed to clone the I/O config instance");
		}
		return null;
	}
	//
	@Override
	public String toString() {
		return super.toString() + "x" + sizeConfig.toString();
	}
	//
	@Override
	public DataLoadBuilder<T, U> setAppConfig(final AppConfig appConfig)
	throws IllegalStateException, RemoteException {
		super.setAppConfig(appConfig);
		setDataSize(appConfig.getItemDataSize());
		setDataRanges(appConfig.getItemDataRanges());
		//
		final String listFilePathStr = appConfig.getItemSrcFile();
		if(itemsFileExists(listFilePathStr)) {
			try {
				setInput(
					new CsvFileItemInput<>(
						Paths.get(listFilePathStr), (Class<T>) ioConfig.getItemClass(),
						ioConfig.getContentSource()
					)
				);
			} catch(final IOException | NoSuchMethodException e) {
				LogUtil.exception(LOG, Level.ERROR, e, "Failed to use CSV file input");
			}
		}
		//
		final String dstFilePath = appConfig.getItemDstFile();
		if(dstFilePath != null && !dstFilePath.isEmpty()) {
			try {
				setOutput(
					new ItemCsvFileOutput<>(
						Paths.get(dstFilePath), (Class<T>) ioConfig.getItemClass(),
						ioConfig.getContentSource()
					)
				);
			} catch(final IOException e) {
				LogUtil.exception(LOG, Level.ERROR, e, "Failed to use CSV file output");
			}
		}
		//
		return this;
	}
	//
	@Override
	public LoadBuilder<T, U> setInput(final Input<T> itemInput)
	throws RemoteException {
		super.setInput(itemInput);
		if(itemInput instanceof FileDataItemInput) {
			final FileDataItemInput<T> fileInput = (FileDataItemInput<T>) itemInput;
			final long approxDataItemsSize = fileInput.getAvgDataSize(
				appConfig.getItemSrcBatchSize()
			);
			ioConfig.setBuffSize(
				approxDataItemsSize < Constants.BUFF_SIZE_LO ?
					Constants.BUFF_SIZE_LO :
					approxDataItemsSize > Constants.BUFF_SIZE_HI ?
						Constants.BUFF_SIZE_HI : (int) approxDataItemsSize
			);
		}
		return this;
	}
	//
	@Override
	public DataLoadBuilder<T, U> setDataSize(final SizeInBytes dataSize)
	throws IllegalArgumentException {
		LOG.debug(Markers.MSG, "Set data item size: {}", dataSize.toString());
		this.sizeConfig = dataSize;
		return this;
	}
	@Override
	public DataLoadBuilder<T, U> setDataRanges(final DataRangesConfig rangesConfig) {
		LOG.debug(Markers.MSG, "Set fixed byte ranges: {}", rangesConfig);
		this.rangesConfig = rangesConfig;
		return this;
	}
}
