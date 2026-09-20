package org.scm4j.vcs.api;

public class VCSTag {
	private final String tagName;
	private final String tagMessage;
	private final String author;
	private final VCSCommit relatedCommit;

	public VCSTag(String tagName, String tagMessage, String author, VCSCommit relatedCommit) {
		this.tagName = tagName;
		this.tagMessage = tagMessage;
		this.author = author;
		this.relatedCommit = relatedCommit;
	}

	public String getTagName() {
		return tagName;
	}

	public String getTagMessage() {
		return tagMessage;
	}

	public String getAuthor() {
		return author;
	}

	public VCSCommit getRelatedCommit() {
		return relatedCommit;
	}

	@Override
	public String toString() {
		return "VCSTag [tagName=" + tagName + ", tagMessage=" + tagMessage + ", author=" + author + ", relatedCommit="
				+ relatedCommit + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((author == null) ? 0 : author.hashCode());
		result = prime * result + ((relatedCommit == null) ? 0 : relatedCommit.hashCode());
		result = prime * result + ((tagMessage == null) ? 0 : tagMessage.hashCode());
		result = prime * result + ((tagName == null) ? 0 : tagName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		VCSTag other = (VCSTag) obj;
		if (author == null) {
			if (other.author != null)
				return false;
		} else if (!author.equals(other.author))
			return false;
		if (relatedCommit == null) {
			if (other.relatedCommit != null)
				return false;
		} else if (!relatedCommit.equals(other.relatedCommit))
			return false;
		if (tagMessage == null) {
			if (other.tagMessage != null)
				return false;
		} else if (!tagMessage.equals(other.tagMessage))
			return false;
		if (tagName == null) {
			if (other.tagName != null)
				return false;
		} else if (!tagName.equals(other.tagName))
			return false;
		return true;
	}
}
