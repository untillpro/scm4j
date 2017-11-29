package org.scm4j.deployer.api;

import lombok.Getter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProductStructure implements IProductStructure {

    @Getter
    private URL defaultDeploymentURL;
    @Getter
    private List<IComponent> components;

    public static ProductStructure create(String defaultDeploymentURL) {
        URL url;
        try {
            url = new URL(defaultDeploymentURL);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid URL: " + defaultDeploymentURL);
        }
        ProductStructure ps = new ProductStructure();
        ps.defaultDeploymentURL = url;
        ps.components = new ArrayList<>();
        return ps;
    }

    public static ProductStructure createEmptyStructure() {
        return new ProductStructure();
    }

    public Component addComponent(String component) {
        Component comp = new Component(component, this);
        this.components.add(comp);
        return comp;
    }
}


