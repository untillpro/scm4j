package org.scm4j.releaser.actions;

import org.scm4j.commons.progress.IProgress;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSRepositoryId;

import java.util.List;

public interface IAction {

	void execute(IProgress progress);

	List<IAction> getChildActions();

	void setParent(IAction parent);

	void addProcessedRepository(VCSRepositoryId repositoryId);

	boolean isRepositoryProcessed(VCSRepositoryId repositoryId);

	Component getComp();

	String toStringAction();

	boolean isExecutable();
}
