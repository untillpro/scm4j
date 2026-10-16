/*
 * Copyright (c) 2026-present unTill Software Development Group B.V.
 * @author Denis Gribanov
 */

package org.scm4j.vcs.git;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.UploadPack;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.scm4j.vcs.api.exceptions.EVCSException;
import org.scm4j.vcs.api.workingcopy.IVCSRepositoryWorkspace;
import org.scm4j.vcs.api.workingcopy.VCSWorkspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GitCloneRetryTest {

	@Rule
	public final TemporaryFolder temporary = new TemporaryFolder();

	private final AtomicInteger packRequests = new AtomicInteger();
	private final AtomicInteger discoveryRequests = new AtomicInteger();
	private final AtomicReference<Throwable> serverFailure = new AtomicReference<>();
	private final List<String> reportedOperations = new ArrayList<>();
	private final List<Throwable> reportedFailures = new ArrayList<>();
	private Git remote;
	private HttpServer server;
	private VCSWorkspace workspace;
	private IVCSRepositoryWorkspace repository;
	private GitVCS vcs;
	private volatile boolean rejectClone;

	@Before
	public void setUp() throws Exception {
		File remoteFolder = temporary.newFolder("remote");
		remote = Git.init().setDirectory(remoteFolder).call();
		FileUtils.writeStringToFile(new File(remoteFolder, "value.txt"), "value", StandardCharsets.UTF_8);
		remote.add().addFilepattern("value.txt").call();
		remote.commit().setMessage("initial content").setAuthor("test", "test@example.test")
				.setCommitter("test", "test@example.test").call();

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/repo.git/", this::serveRepository);
		server.start();
		workspace = new VCSWorkspace(temporary.newFolder("workspace").getPath());
		repository = workspace.getVCSRepositoryWorkspace(
				"http://127.0.0.1:" + server.getAddress().getPort() + "/repo.git");
		vcs = new GitVCS(repository);
		vcs.setRetryStatusReporter((operation, failure) -> {
			reportedOperations.add(operation);
			reportedFailures.add(failure);
		});
	}

	@After
	public void tearDown() {
		if (server != null) {
			server.stop(0);
		}
		if (remote != null) {
			remote.close();
		}
		assertNull("Test HTTP server failed: " + serverFailure.get(), serverFailure.get());
	}

	@Test(timeout = 15000)
	public void retriesTruncatedPackForSharedStatusSnapshot() {
		assertEquals("value", vcs.getFileContent(null, "value.txt", null));
		GitVCS sibling = new GitVCS(workspace.getVCSRepositoryWorkspace(repository.getRepoUrl(), "component"));
		assertEquals("value", sibling.getFileContent(null, "value.txt", null));
		assertCloneRetriedOnce();
		assertFalse(new File(repository.getPreparedRepositoryFolder(), "value.txt").exists());
		assertFalse(repository.getWritableWorkingCopiesFolder().exists());
	}

	@Test(timeout = 15000)
	public void retriesTruncatedPackForWritableCheckout() throws Exception {
		File checkout = new File(temporary.getRoot(), "checkout");
		try (Git ignored = vcs.getLocalGit(checkout.getPath())) {
			assertEquals("value", FileUtils.readFileToString(new File(checkout, "value.txt"),
					StandardCharsets.UTF_8));
		}
		assertCloneRetriedOnce();
	}

	@Test(timeout = 15000)
	public void doesNotRetryPermanentCloneFailure() {
		rejectClone = true;
		try {
			vcs.getFileContent(null, "value.txt", null);
			fail("Clone should fail when access is forbidden");
		} catch (EVCSException expected) {
			assertFalse(GitVCS.isTransientTransportFailure(expected));
		}
		assertTrue(discoveryRequests.get() > 0);
		assertEquals(0, packRequests.get());
		assertTrue(reportedOperations.isEmpty());
	}

	private void assertCloneRetriedOnce() {
		assertEquals(2, packRequests.get());
		assertEquals(1, reportedOperations.size());
		assertEquals("Git clone", reportedOperations.get(0));
		Throwable cause = reportedFailures.get(0);
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		assertTrue("Expected a truncated-pack EOF, got: " + cause, cause instanceof EOFException);
	}

	private void serveRepository(HttpExchange exchange) throws IOException {
		try {
			ByteArrayOutputStream response = new ByteArrayOutputStream();
			boolean discovery = exchange.getRequestURI().getPath().endsWith("/info/refs");
			if (discovery) {
				discoveryRequests.incrementAndGet();
				if (rejectClone) {
					exchange.sendResponseHeaders(403, -1);
					return;
				}
			}
			try (UploadPack upload = new UploadPack(remote.getRepository())) {
				upload.setBiDirectionalPipe(false);
				if (discovery) {
					exchange.getResponseHeaders().set("Content-Type", "application/x-git-upload-pack-advertisement");
					upload.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(new PacketLineOut(response)),
							"git-upload-pack");
				} else {
					exchange.getResponseHeaders().set("Content-Type", "application/x-git-upload-pack-result");
					InputStream requestBody = exchange.getRequestBody();
					if ("gzip".equals(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
						requestBody = new GZIPInputStream(requestBody);
					}
					byte[] request = IOUtils.toByteArray(requestBody);
					upload.upload(new ByteArrayInputStream(request), response, null);
				}
			}
			byte[] body = response.toByteArray();
			// End the first pack response early, as in an interrupted HTTP download.
			int sentLength = !discovery && packRequests.incrementAndGet() == 1 ? body.length / 2 : body.length;
			exchange.sendResponseHeaders(200, sentLength);
			exchange.getResponseBody().write(body, 0, sentLength);
		} catch (IOException | RuntimeException failure) {
			serverFailure.compareAndSet(null, failure);
			throw failure;
		} finally {
			exchange.close();
		}
	}
}
