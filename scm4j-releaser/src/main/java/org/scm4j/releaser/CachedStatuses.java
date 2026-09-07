package org.scm4j.releaser;

import java.util.concurrent.ConcurrentHashMap;

import org.scm4j.releaser.conf.VCSRepositoryId;

@SuppressWarnings("serial")
public class CachedStatuses extends ConcurrentHashMap<VCSRepositoryId, ExtendedStatus> {

}
