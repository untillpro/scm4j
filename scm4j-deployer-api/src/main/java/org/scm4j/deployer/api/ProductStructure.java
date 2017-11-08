package org.scm4j.deployer.api;

import lombok.Getter;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ProductStructure implements IProductStructure {

    @Getter
    private final URL defaultDeploymentURL;
    @Getter private List<IComponent> components;

    private ProductStructure(URL defaultDeploymentURL) {
        this.defaultDeploymentURL = defaultDeploymentURL;
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

    public static ProductStructure create(String defaultDeploymentURL) {
        URL url;
        try {
            url = new URL(defaultDeploymentURL);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid URL: " + defaultDeploymentURL);
        }
        return new ProductStructure(url);
    }

}


