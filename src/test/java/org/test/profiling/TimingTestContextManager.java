package org.test.profiling;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.lang.Nullable;
import org.springframework.test.context.TestContextBootstrapper;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.util.ReflectionUtils;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

public class TimingTestContextManager extends TestContextManager {
	
	private static final Log logger = LogFactory.getLog(TimingTestContextManager.class);
	
	static SimpleMeterRegistry registry = new SimpleMeterRegistry();
	private static final List<String> includeListeners = Arrays.asList("CachingTransactionalTestExecutionListener", "TransactionalTestExecutionListener");

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			Collection<Timer> timers = registry.get("beforeTestClass").timers();

			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {				
				logTimer("beforeTestClass", timer);
			}

			timers = registry.get("prepareTestInstance").timers();
			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {
				logTimer("prepareTestInstance", timer);
			}
			timers = registry.get("beforeTestMethod").timers();
			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {				
				logTimer("beforeTestMethod", timer);
			}
			timers = registry.get("beforeTestExecution").timers();
			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {
				logTimer("beforeTestExecution", timer);
			}
			timers = registry.get("afterTestExecution").timers();
			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {
				logTimer("afterTestExecution", timer);
			}
			timers = registry.get("afterTestMethod").timers();
			System.out.println("------------------------------------------------------------------------------------------------");
			for(Timer timer : timers) {
				logTimer("afterTestMethod", timer);
			}			
		}));
	}
	
	private static void logTimer(String name, Timer timer) {

//		if (!includeListeners.contains(timer.getId().getTag("listener"))) {
//			return;
//		}
		
		Double totalTime = timer.totalTime(TimeUnit.MILLISECONDS);
		System.out.print(String.format("%1$-20s ", name));
		System.out.print(String.format("%1$55s - ", timer.getId().getTag("listener")));
		System.out.print(String.format("Total Time: %1$15fms, ", totalTime));
		System.out.print(String.format("Count: %1$-8d, ", timer.count()));
		System.out.print(String.format("Mean: %1$20fms, ", totalTime / timer.count()));
		System.out.print(String.format("Max: %1$12fms, ", timer.max(TimeUnit.MILLISECONDS)));
		ValueAtPercentile[] percentiles = timer.takeSnapshot().percentileValues();
		System.out.print("Percentiles: ");
		for (ValueAtPercentile valueAtPercentile : percentiles) {
			System.out.print(String.format("(%1$10fms at %2$2d" , valueAtPercentile.value(TimeUnit.MILLISECONDS), (int)(valueAtPercentile.percentile() * 100)) + "%)");
		}
		System.out.println("");
	}
	public TimingTestContextManager(Class<?> testClass) {
		super(testClass);
	}

	public TimingTestContextManager(TestContextBootstrapper testContextBootstrapper) {
		super(testContextBootstrapper);
	}


	@Override
	public void beforeTestClass() throws Exception {
		Class<?> testClass = getTestContext().getTestClass();
		if (logger.isTraceEnabled()) {
			logger.trace("beforeTestClass(): class [" + testClass.getName() + "]");
		}
		getTestContext().updateState(null, null, null);

		for (TestExecutionListener testExecutionListener : getTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.beforeTestClass(getTestContext());
			}
			catch (Throwable ex) {
				logException(ex, "beforeTestClass", testExecutionListener, testClass);
				ReflectionUtils.rethrowException(ex);
			} finally {
				sample.stop(getTimer("beforeTestClass", testExecutionListener.getClass().getSimpleName()));
			}
		}
	}
	
	private Timer getTimer(String name, String listener) {
		return Timer.builder(name)
				.tag("listener",  listener)
				.publishPercentiles(.50,.75, .90)
				.register(registry);
	}
	@Override
	public void prepareTestInstance(Object testInstance) throws Exception {
		if (logger.isTraceEnabled()) {
			logger.trace("prepareTestInstance(): instance [" + testInstance + "]");
		}
		getTestContext().updateState(testInstance, null, null);

		for (TestExecutionListener testExecutionListener : getTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.prepareTestInstance(getTestContext());
			}
			catch (Throwable ex) {
				if (logger.isErrorEnabled()) {
					logger.error("Caught exception while allowing TestExecutionListener [" + testExecutionListener +
							"] to prepare test instance [" + testInstance + "]", ex);
				}
				ReflectionUtils.rethrowException(ex);
			} finally {
				sample.stop(getTimer("prepareTestInstance", testExecutionListener.getClass().getSimpleName()));
			}
		}
	}

	@Override
	public void beforeTestMethod(Object testInstance, Method testMethod) throws Exception {
		String callbackName = "beforeTestMethod";
		prepareForBeforeCallback(callbackName, testInstance, testMethod);

		for (TestExecutionListener testExecutionListener : getTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.beforeTestMethod(getTestContext());
			}
			catch (Throwable ex) {
				handleBeforeException(ex, callbackName, testExecutionListener, testInstance, testMethod);
			} finally {						
				sample.stop(getTimer("beforeTestMethod", testExecutionListener.getClass().getSimpleName()));
			}
		}
	}

	@Override
	public void beforeTestExecution(Object testInstance, Method testMethod) throws Exception {
		String callbackName = "beforeTestExecution";
		prepareForBeforeCallback(callbackName, testInstance, testMethod);

		for (TestExecutionListener testExecutionListener : getTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.beforeTestExecution(getTestContext());
			}
			catch (Throwable ex) {
				handleBeforeException(ex, callbackName, testExecutionListener, testInstance, testMethod);
			} finally {
				sample.stop(getTimer("beforeTestExecution", testExecutionListener.getClass().getSimpleName()));
			}
		}
	}

	@Override
	public void afterTestExecution(Object testInstance, Method testMethod, @Nullable Throwable exception)
			throws Exception {

		String callbackName = "afterTestExecution";
		prepareForAfterCallback(callbackName, testInstance, testMethod, exception);
		Throwable afterTestExecutionException = null;

		// Traverse the TestExecutionListeners in reverse order to ensure proper
		// "wrapper"-style execution of listeners.
		for (TestExecutionListener testExecutionListener : getReversedTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.afterTestExecution(getTestContext());
			}
			catch (Throwable ex) {
				logException(ex, callbackName, testExecutionListener, testInstance, testMethod);
				if (afterTestExecutionException == null) {
					afterTestExecutionException = ex;
				}
				else {
					afterTestExecutionException.addSuppressed(ex);
				}
			} finally {
				sample.stop(getTimer("afterTestExecution", testExecutionListener.getClass().getSimpleName()));
			}
		}

		if (afterTestExecutionException != null) {
			ReflectionUtils.rethrowException(afterTestExecutionException);
		}
	}

	@Override
	public void afterTestMethod(Object testInstance, Method testMethod, @Nullable Throwable exception)
			throws Exception {

		String callbackName = "afterTestMethod";
		prepareForAfterCallback(callbackName, testInstance, testMethod, exception);
		Throwable afterTestMethodException = null;

		// Traverse the TestExecutionListeners in reverse order to ensure proper
		// "wrapper"-style execution of listeners.
		for (TestExecutionListener testExecutionListener : getReversedTestExecutionListeners()) {
			Timer.Sample sample = Timer.start(registry);
			try {
				testExecutionListener.afterTestMethod(getTestContext());
			}
			catch (Throwable ex) {
				logException(ex, callbackName, testExecutionListener, testInstance, testMethod);
				if (afterTestMethodException == null) {
					afterTestMethodException = ex;
				}
				else {
					afterTestMethodException.addSuppressed(ex);
				}
			} finally {
				sample.stop(getTimer("afterTestMethod", testExecutionListener.getClass().getSimpleName()));
			}
		}

		if (afterTestMethodException != null) {
			ReflectionUtils.rethrowException(afterTestMethodException);
		}
	}
	
	/**
	 * Get a copy of the {@link TestExecutionListener TestExecutionListeners}
	 * registered for this {@code TestContextManager} in reverse order.
	 */
	private List<TestExecutionListener> getReversedTestExecutionListeners() {
		List<TestExecutionListener> listenersReversed = new ArrayList<>(getTestExecutionListeners());
		Collections.reverse(listenersReversed);
		return listenersReversed;
	}
	
	private void prepareForBeforeCallback(String callbackName, Object testInstance, Method testMethod) {
		if (logger.isTraceEnabled()) {
			logger.trace(String.format("%s(): instance [%s], method [%s]", callbackName, testInstance, testMethod));
		}
		getTestContext().updateState(testInstance, testMethod, null);
	}

	private void prepareForAfterCallback(String callbackName, Object testInstance, Method testMethod,
			@Nullable Throwable exception) {

		if (logger.isTraceEnabled()) {
			logger.trace(String.format("%s(): instance [%s], method [%s], exception [%s]",
					callbackName, testInstance, testMethod, exception));
		}
		getTestContext().updateState(testInstance, testMethod, exception);
	}
	
	private void handleBeforeException(Throwable ex, String callbackName, TestExecutionListener testExecutionListener,
			Object testInstance, Method testMethod) throws Exception {

		logException(ex, callbackName, testExecutionListener, testInstance, testMethod);
		ReflectionUtils.rethrowException(ex);
	}
	
	private void logException(
			Throwable ex, String callbackName, TestExecutionListener testExecutionListener, Class<?> testClass) {

		if (logger.isWarnEnabled()) {
			logger.warn(String.format("Caught exception while invoking '%s' callback on " +
					"TestExecutionListener [%s] for test class [%s]", callbackName, testExecutionListener,
					testClass), ex);
		}
	}

	private void logException(Throwable ex, String callbackName, TestExecutionListener testExecutionListener,
			Object testInstance, Method testMethod) {

		if (logger.isWarnEnabled()) {
			logger.warn(String.format("Caught exception while invoking '%s' callback on " +
					"TestExecutionListener [%s] for test method [%s] and test instance [%s]",
					callbackName, testExecutionListener, testMethod, testInstance), ex);
		}
	}
	
	
}
