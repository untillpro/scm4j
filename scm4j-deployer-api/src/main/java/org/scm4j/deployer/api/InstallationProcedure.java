package org.scm4j.deployer.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Data
public class InstallationProcedure implements IInstallationProcedure {

    private List<IAction> actions = new ArrayList<>();

}