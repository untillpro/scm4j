package org.scm4j.deployer.api;

import java.util.Collections;
import java.util.List;

public interface IProduct {
    IProductStructure getProductStructure();
    default List<IProduct> getDependentProducts() {
        return Collections.emptyList();
    }
}
