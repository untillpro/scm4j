package org.scm4j.deployer.api;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProductStructure implements IProductStructure {

	@Getter
	private String defaultDeploymentPath;
	@Getter
	private List<IComponent> components;

	private ProductStructure(String defaultDeploymentPath) {
		this.defaultDeploymentPath = defaultDeploymentPath;
	}

	public static ProductStructure create(String defaultDeploymentPath) {
		ProductStructure ps = new ProductStructure(defaultDeploymentPath);
		ps.components = new ArrayList<>();
		return ps;
	}

	public static ProductStructure createEmptyStructure() {
		ProductStructure ps = new ProductStructure("");
		ps.components = Collections.emptyList();
		return ps;
	}

	public Component addComponent(String component) {
		Component comp = new Component(component, this);
		this.components.add(comp);
		return comp;
	}
}


