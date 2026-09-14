package org.scm4j.releaser.conf;

import java.util.Objects;

import org.scm4j.releaser.builders.IBuilder;
import org.scm4j.vcs.api.IVCS;

public class VCSRepository {

	public static final String DEFAULT_RELEASE_BRANCH_PREFIX = "release/";
	public static final String DEFAULT_DEVELOP_BRANCH = null;

	private final String name;
	private final VCSComponentLocation componentLocation;
	private final Credentials credentials;
	private final VCSType type;
	private final String developBranch;
	private final String releaseBranchPrefix;
	private final IVCS vcs;
	private final IBuilder builder;

	@Override
	public int hashCode() {
		return Objects.hashCode(componentLocation);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VCSRepository other = (VCSRepository) obj;
		return Objects.equals(componentLocation, other.componentLocation);
	}

	public String getReleaseBranchPrefix() {
		return releaseBranchPrefix;
	}

	public String getDevelopBranch() {
		return developBranch;
	}

	public String getUrl() {
		return componentLocation.getUrl();
	}

	public String getSubfolder() {
		return componentLocation.getSubfolder();
	}

	public VCSComponentLocation getComponentLocation() {
		return componentLocation;
	}

	public String getComponentPath(String relativePath) {
		return componentLocation.joinPath(relativePath);
	}

	public String getName() {
		return name;
	}

	public Credentials getCredentials() {
		return credentials;
	}


	public VCSType getType() {
		return type;
	}

	public VCSRepository(String name, String url, Credentials credentials,
						 VCSType type, String developBranch, String releaseBranchPrefix, IVCS vcs, IBuilder builder) {
		this(name, new VCSComponentLocation(url, null), credentials, type, developBranch, releaseBranchPrefix, vcs, builder);
	}

	public VCSRepository(String name, String url, String subfolder, Credentials credentials,
						 VCSType type, String developBranch, String releaseBranchPrefix, IVCS vcs, IBuilder builder) {
		this(name, new VCSComponentLocation(url, subfolder), credentials, type, developBranch, releaseBranchPrefix, vcs, builder);
	}

	VCSRepository(String name, VCSComponentLocation componentLocation, Credentials credentials,
				  VCSType type, String developBranch, String releaseBranchPrefix, IVCS vcs, IBuilder builder) {
		this.name = name;
		this.componentLocation = componentLocation;
		this.credentials = credentials;
		this.type = type;
		this.developBranch = developBranch;
		this.vcs = vcs;
		this.releaseBranchPrefix = releaseBranchPrefix;
		this.builder = builder;
	}

	@Override
	public String toString() {
		return "VCSRepository [url=" + getUrl() + "]";
	}

	public IVCS getVCS() {
		return vcs;
	}

	public IBuilder getBuilder() {
		return builder;
	}

}
