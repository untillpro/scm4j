package org.scm4j.deployer.api;

import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

public interface IProduct {
    IProductStructure getProductStructure();

    int removeLegacyProduct();

    boolean isInstalled();

    default List<String> getDependentProducts() {
        return Collections.emptyList();
    }

    @Deprecated
    @SneakyThrows
    default boolean isInstalled(String productServiceName) {
        Process p = Runtime.getRuntime().exec("sc queryex type=service");
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(productServiceName)) {
                return true;
            }
        }
        return false;
    }
}
