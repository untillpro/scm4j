package org.scm4j.commons;

import org.apache.commons.lang3.StringUtils;

public class CommentedString {

	private static final String DEFAULT_COMMENT_SIGN = "#";
	private final String strNoComment;
	private final String comment;
	private final String sourceString;

	public CommentedString(String str) {
		this(str, DEFAULT_COMMENT_SIGN);
	}

	public CommentedString(String str, String commentSign) {
		sourceString = str;
		Integer pos = str.indexOf(commentSign);

		if (pos >= 0) {
			// add spaces between valuable chars and # to comment
			comment = StringUtils.difference(str.substring(0, pos).trim(), ltrim(str.substring(0, pos)))
					+ str.substring(pos);
			strNoComment = rtrim(str.substring(0, pos));
		} else {
			comment = "";
			strNoComment = str;
		}
	}

	public Boolean isValuable() {
		return !strNoComment.trim().isEmpty();
	}

	public String getStrNoComment() {
		return strNoComment;
	}

	public String getComment() {
		return comment;
	}

	private String ltrim(String s) {
		int i = 0;
		while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
			i++;
		}
		return s.substring(i);
	}

	private String rtrim(String s) {
		int i = s.length() - 1;
		while (i > 0 && Character.isWhitespace(s.charAt(i))) {
			i--;
		}
		return s.substring(0, i + 1);
	}

	@Override
	public String toString() {
		return sourceString;
	}
}
