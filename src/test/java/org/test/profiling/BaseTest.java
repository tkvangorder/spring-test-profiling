package org.test.profiling;

import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * This base testing class turns off eureka and remote configuration, sets the profile to unit and defines the spring runner. This class can be used
 * as a base class for unit tests that wish to leverage test slices in spring boot.
 */
@TestPropertySource(properties = {
		"eureka.client.enabled=false",
		"spring.cache.type=none",
		"spring.cloud.config.enabled=false",
		"spring.cloud.stream.enabled=false"
})
@ActiveProfiles(profiles = "unit")
@RunWith(CustomSpringRunner.class)
@SpringBootTest
public abstract class BaseTest implements ApplicationContextAware {

	protected static final Logger logger = LoggerFactory.getLogger(BaseTest.class);

	protected ApplicationContext applicationContext;

	@Autowired
	private AutowireCapableBeanFactory beanFactory;

	/**
	 * This method can used to have spring "decorate" an instance of a bean that was constructed outside the application context. The
	 * bean name can be used when more than one instance of the object exists in the application context.
	 *
	 * This method is very useful when injecting mocks into a singleton bean, as the bean can be constructed in the test's setup method,
	 * wired with its dependencies, and then the test can explicitly set mocks into that instance without affecting the singleton that
	 * exists in the spring context.
	 *
	 * @param bean A bean that will have its dependencies injected into it.
	 * @param name The bean definition name that will be used to resolve dependencies on the passed in instance.
	 */
	protected void configureBean(Object bean, String name) {
		beanFactory.configureBean(bean, name);
	}


	/**
	 * This method can used to have spring "decorate" an instance of a bean that was constructed outside the application context.
	 * This method is very useful when injecting mocks into a singleton bean, as the bean can be constructed in the test's setup method,
	 * wired with its dependencies, and then the test can explicitly set mocks into that instance without affecting the singleton that
	 * exists in the spring context.
	 *
	 * @param bean A bean that will have its dependencies injected into it.
	 */
	protected void configureBean(Object bean) {
		beanFactory.autowireBeanProperties(bean, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, true);
	}

	/**
	 * Set the {@link ApplicationContext} to be used by this test instance,
	 * provided via {@link ApplicationContextAware} semantics.
	 * @param applicationContext the ApplicationContext that this test runs in
	 */
	@Override
	public void setApplicationContext(final ApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}

}
