package org.test.profiling;

import org.junit.runners.model.InitializationError;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

public class CustomSpringRunner extends SpringJUnit4ClassRunner {

	public CustomSpringRunner(Class<?> clazz) throws InitializationError {
		super(clazz);
	}

	@Override
	protected TestContextManager createTestContextManager(Class<?> clazz) {
		return new TimingTestContextManager(clazz);
	}
	
	
}
