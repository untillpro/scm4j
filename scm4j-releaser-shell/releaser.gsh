@GrabResolver(name='jitpack', root='https://jitpack.io')
@Grab('com.github.scm4j:scm4j-releaser:21.0')

import org.scm4j.releaser.cli.CLI;

class CLIRunner {
	static void main(args) {
		System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "64");
		CLI.main(args);
	}
}