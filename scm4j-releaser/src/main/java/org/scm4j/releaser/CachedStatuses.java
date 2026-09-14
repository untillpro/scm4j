package org.scm4j.releaser;

import java.util.concurrent.ConcurrentHashMap;

import org.scm4j.releaser.conf.VCSComponentLocation;

@SuppressWarnings("serial")
public class CachedStatuses extends ConcurrentHashMap<VCSComponentLocation, ExtendedStatus> {

}
