package org.test.profiling;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.test.context.DefaultTestExecutionListenersPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.util.ReflectionUtils;

public class CachingTransactionalTestExecutionListener extends TransactionalTestExecutionListener {
	
	//intentionally using the base class to keep the logger name the same as it was before.
	private static final Log logger = LogFactory.getLog(TransactionalTestExecutionListener.class);
	
	/**
	 * A cache of class -> methods annotated with BeforeTransaction.
	 */
	private static LruClassMethodCache beforeTransactionMethodCache = new LruClassMethodCache(4);

	/**
	 * A cache of class -> methods annotated with AfterTransaction.
	 */
	private static LruClassMethodCache afterTransactionMethodCache = new LruClassMethodCache(4);
	

	
	/**
	 * Profiling of the TransactionalTestExecutionListener shows a hotspot in both the runBeforerTransactionalMethods.
	 * 
	 * Using LRU cache (for the annotated before/after methods to see if this helps reduce calls within the hotspot.
	 * 
	 * @param testContext the current test context
	 */
	@Override
	protected void runBeforeTransactionMethods(TestContext testContext) throws Exception {

		try {
			List<Method> methods = getBeforeTransactionMethods(testContext.getTestClass());
			for (Method method : methods) {
				if (logger.isDebugEnabled()) {
					logger.debug("Executing @BeforeTransaction method [" + method + "] for test context " + testContext);
				}
				method.invoke(testContext.getTestInstance());
			}			
		}
		catch (InvocationTargetException ex) {
			if (logger.isErrorEnabled()) {
				logger.error("Exception encountered while executing @BeforeTransaction methods for test context " +
						testContext + ".", ex.getTargetException());
			}
			ReflectionUtils.rethrowException(ex.getTargetException());
		}
	}

	private List<Method> getBeforeTransactionMethods(Class<?> testClass) {
		List<Method> methods = beforeTransactionMethodCache.get(testClass);
		if (methods == null) {

			methods = getAnnotatedMethods(testClass, BeforeTransaction.class);
			Collections.reverse(methods);
			for (Method method : methods) {
				ReflectionUtils.makeAccessible(method);
			}
			beforeTransactionMethodCache.put(testClass, methods);
		}
		return methods;
	}

	/**
	 * Profiling of the TransactionalTestExecutionListener shows a hotspot in both the runAfterTransactionalMethods.
	 * 
	 * Using LRU cache (for the annotated after methods to see if this helps reduce calls within the hotspot)
	 * 
	 * @param testContext the current test context
	 */
		@Override
		protected void runAfterTransactionMethods(TestContext testContext) throws Exception {
			Throwable afterTransactionException = null;
	
			List<Method> methods = getAfterTransactionMethods(testContext.getTestClass());
			
			for (Method method : methods) {			
				try {
					if (logger.isDebugEnabled()) {
						logger.debug("Executing @AfterTransaction method [" + method + "] for test context " + testContext);
					}
					method.invoke(testContext.getTestInstance());
				}
				catch (InvocationTargetException ex) {
					Throwable targetException = ex.getTargetException();
					if (afterTransactionException == null) {
						afterTransactionException = targetException;
					}
					logger.error("Exception encountered while executing @AfterTransaction method [" + method +
							"] for test context " + testContext, targetException);
				}
				catch (Exception ex) {
					if (afterTransactionException == null) {
						afterTransactionException = ex;
					}
					logger.error("Exception encountered while executing @AfterTransaction method [" + method +
							"] for test context " + testContext, ex);
				}
			}
	
			if (afterTransactionException != null) {
				ReflectionUtils.rethrowException(afterTransactionException);
			}
		}

	private List<Method> getAfterTransactionMethods(Class<?> testClass) {
		List<Method> methods = afterTransactionMethodCache.get(testClass);
		if (methods == null) {
			methods = getAnnotatedMethods( testClass, AfterTransaction.class);
			for (Method method : methods) {			
					ReflectionUtils.makeAccessible(method);
			}
			afterTransactionMethodCache.put( testClass, methods);
		}
		return methods;
	}
	
	private List<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotationType) {
		return Arrays.stream(ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS))
				.filter(method -> AnnotatedElementUtils.hasAnnotation(method, annotationType))
				.collect(Collectors.toList());
	}

	/**
	 * Get all methods in the supplied {@link Class class} and its superclasses
	 * which are annotated with the supplied {@code annotationType} but
	 * which are not <em>shadowed</em> by methods overridden in subclasses.
	 * <p>Default methods on interfaces are also detected.
	 * @param clazz the class for which to retrieve the annotated methods
	 * @param annotationType the annotation type for which to search
	 * @return all annotated methods in the supplied class and its superclasses
	 * as well as annotated interface default methods
	 */
	static class PostProcessor implements DefaultTestExecutionListenersPostProcessor {

		@Override
		public Set<Class<? extends TestExecutionListener>> postProcessDefaultTestExecutionListeners(
				Set<Class<? extends TestExecutionListener>> listeners) {
//			Set<Class<? extends TestExecutionListener>> updated = new LinkedHashSet<>(listeners.size());
//			for (Class<? extends TestExecutionListener> listener : listeners) {
//				if (listener.getSimpleName().equals("MockRestServiceServerResetTestExecutionListener")
//						|| listener.getSimpleName().equals("RestDocsTestExecutionListener")) {
//					continue;
//				}
//				updated.add(listener.equals(TransactionalTestExecutionListener.class)
//						? CachingTransactionalTestExecutionListener.class : listener);
//			}
//			return updated;
			return listeners;
		}
	}

	
	private static class LruClassMethodCache extends LinkedHashMap<Class<?>, List<Method>> {

		/**
		 * Create a new {@code LruCache} with the supplied initial capacity
		 * and load factor.
		 * @param initialCapacity the initial capacity
		 * @param loadFactor the load factor
		 */
		LruClassMethodCache(int initialCapacity) {
			super(initialCapacity);
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<Class<?>, List<Method>> eldest) {
			if (size() > 4) {
				return true;
			} else {
				return false;
			}
		}
	}
	
}
