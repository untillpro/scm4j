package org.scm4j.releaser.scmactions.procs;

import org.scm4j.releaser.progress.IProgress;
import org.scm4j.vcs.api.IVCSMutationSession;

public interface ISCMProc {

	void execute(IProgress progress, IVCSMutationSession vcs);

}
