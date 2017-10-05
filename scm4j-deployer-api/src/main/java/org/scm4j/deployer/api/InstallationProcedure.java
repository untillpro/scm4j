package org.scm4j.deployer.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class InstallationProcedure implements IInstallationProcedure {

    final private List<IAction> actions;

}