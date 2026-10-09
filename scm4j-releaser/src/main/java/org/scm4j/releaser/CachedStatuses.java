package org.scm4j.releaser;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.scm4j.releaser.conf.VCSComponentLocation;

public class CachedStatuses {
	private final ConcurrentHashMap<VCSComponentLocation, CompletableFuture<ExtendedStatus>> statuses =
			new ConcurrentHashMap<>();

	public ExtendedStatus get(VCSComponentLocation componentLocation) {
		CompletableFuture<ExtendedStatus> status = statuses.get(componentLocation);
		return status == null ? null : status.join();
	}

	public synchronized ExtendedStatus put(VCSComponentLocation componentLocation, ExtendedStatus status) {
		return completedStatusOrNull(statuses.put(componentLocation, CompletableFuture.completedFuture(status)));
	}

	public synchronized ExtendedStatus replace(VCSComponentLocation componentLocation, ExtendedStatus status) {
		return completedStatusOrNull(statuses.replace(componentLocation, CompletableFuture.completedFuture(status)));
	}

	public synchronized ExtendedStatus remove(VCSComponentLocation componentLocation) {
		return completedStatusOrNull(statuses.remove(componentLocation));
	}

	public int size() {
		return statuses.size();
	}

	synchronized CompletableFuture<ExtendedStatus> putIfAbsent(VCSComponentLocation componentLocation,
			CompletableFuture<ExtendedStatus> candidate) {
		return statuses.putIfAbsent(componentLocation, candidate);
	}

	synchronized boolean remove(VCSComponentLocation componentLocation, CompletableFuture<ExtendedStatus> candidate) {
		if (statuses.get(componentLocation) != candidate) {
			return false;
		}
		remove(componentLocation);
		return true;
	}

	private ExtendedStatus completedStatusOrNull(CompletableFuture<ExtendedStatus> status) {
		if (status == null || !status.isDone() || status.isCompletedExceptionally() || status.isCancelled()) {
			return null;
		}
		return status.join();
	}

}
