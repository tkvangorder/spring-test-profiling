package org.test.profiling;

import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Import(BaseTransactionalTest.Configuration.class)
public class BaseTransactionalTest extends BaseTest {

	protected static class Configuration {
		
	}
}
