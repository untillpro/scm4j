package org.scm4j.deployer.api;

import java.util.Collections;
import java.util.List;

public interface IProduct {

	IProductStructure getProductStructure();

	default List<String> getDependentProducts() {
		return Collections.emptyList();
	}

}
