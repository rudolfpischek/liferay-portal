/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.increment;

import com.liferay.portal.kernel.concurrent.BatchablePipe;
import com.liferay.portal.kernel.increment.Increment;
import com.liferay.portal.kernel.util.NamedThreadFactory;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.security.pacl.PACLClassLoaderUtil;

import java.io.Serializable;

import java.lang.reflect.Method;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Shuyang Zhou
 */
public class BufferedIncrementProcessor {

	public BufferedIncrementProcessor(
		BufferedIncrementConfiguration bufferedIncrementConfiguration,
		Method method) {

		_batchablePipe = new BatchablePipe<Serializable, Increment<?>>();
		_bufferedIncrementConfiguration = bufferedIncrementConfiguration;

		ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
			0, _bufferedIncrementConfiguration.getThreadpoolMaxSize(),
			_bufferedIncrementConfiguration.getThreadpoolKeepAliveTime(),
			TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

		threadPoolExecutor.setRejectedExecutionHandler(new DiscardPolicy());

		Class<?>[] parameterTypes = method.getParameterTypes();

		StringBundler sb = new StringBundler(parameterTypes.length * 2 + 5);

		sb.append("BufferedIncreament-");
		sb.append(method.getDeclaringClass().getSimpleName());
		sb.append(StringPool.PERIOD);
		sb.append(method.getName());
		sb.append(StringPool.OPEN_PARENTHESIS);

		for (Class<?> parameterType : parameterTypes) {
			sb.append(parameterType.getSimpleName());
			sb.append(StringPool.COMMA);
		}

		sb.setIndex(sb.index() - 1);
		sb.append(StringPool.CLOSE_PARENTHESIS);

		threadPoolExecutor.setThreadFactory(
			new NamedThreadFactory(
				sb.toString(), Thread.NORM_PRIORITY,
				PACLClassLoaderUtil.getContextClassLoader()));

		_executorService = threadPoolExecutor;
		_queueLengthTracker = new AtomicInteger();
	}

	public void destroy() {
		_executorService.shutdown();
	}

	@SuppressWarnings("rawtypes")
	public void process(BufferedIncreasableEntry bufferedIncreasableEntry) {
		if (_batchablePipe.put(bufferedIncreasableEntry)) {
			_executorService.execute(
				new BufferedIncrementRunnable(
					_bufferedIncrementConfiguration, _batchablePipe,
						_queueLengthTracker));
		}
	}

	private final BatchablePipe<Serializable, Increment<?>> _batchablePipe;
	private final BufferedIncrementConfiguration
		_bufferedIncrementConfiguration;
	private final ExecutorService _executorService;
	private final AtomicInteger _queueLengthTracker;

}