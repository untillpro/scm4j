package org.scm4j.releaser.actions;

import org.scm4j.releaser.progress.IProgress;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.VCSComponentLocation;

import java.util.List;

public interface IAction {

	void execute(IProgress progress);

	List<IAction> getChildActions();

	void setParent(IAction parent);

	void addProcessedRepository(VCSComponentLocation componentLocation);

	boolean isRepositoryProcessed(VCSComponentLocation componentLocation);

	Component getComp();

	String toStringAction();

	boolean isExecutable();
}
