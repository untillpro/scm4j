package org.scm4j.deployer.api;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class ProductStructure implements IProductStructure {

    @Getter private List<IComponent> components;

    private ProductStructure() {
    }

    public Component addComponent(String component) {
        Component comp = new Component(component, this);
        if (this.components == null) {
            this.components = new ArrayList<>();
            this.components.add(comp);
        } else {
            this.components.add(comp);
        }
        return comp;
    }

    public static ProductStructure create() {
        return new ProductStructure();
    }
}


