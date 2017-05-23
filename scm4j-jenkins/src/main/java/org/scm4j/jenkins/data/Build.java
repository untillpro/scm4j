package org.scm4j.jenkins.data;

public class Build {

	private Boolean building;
	private String decription;
	private String displayName;
	private Integer duration;
	private Integer estimatedDuration;
	private String executor;
	private String fullDisplayName;
	private String id;
	private Boolean keepLog;
	private Integer number;
	private Integer queueId;
	private String result;
	private Long timestamp;
	private String url;

	public Build() {

	}

	public Build(Boolean building, String decription, String displayName, Integer duration,
			Integer estimatedDuration, String executor, String fullDisplayName, String id,
			Boolean keepLog, Integer number, Integer queueId, String result, Long timestamp,
			String url) {
		super();
		this.building = building;
		this.decription = decription;
		this.displayName = displayName;
		this.duration = duration;
		this.estimatedDuration = estimatedDuration;
		this.executor = executor;
		this.fullDisplayName = fullDisplayName;
		this.id = id;
		this.keepLog = keepLog;
		this.number = number;
		this.queueId = queueId;
		this.result = result;
		this.timestamp = timestamp;
		this.url = url;
	}

	public Boolean getBuilding() {
		return building;
	}

	public void setBuilding(Boolean building) {
		this.building = building;
	}

	public String getDecription() {
		return decription;
	}

	public void setDecription(String decription) {
		this.decription = decription;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public Integer getDuration() {
		return duration;
	}

	public void setDuration(Integer duration) {
		this.duration = duration;
	}

	public Integer getEstimatedDuration() {
		return estimatedDuration;
	}

	public void setEstimatedDuration(Integer estimatedDuration) {
		this.estimatedDuration = estimatedDuration;
	}

	public String getExecutor() {
		return executor;
	}

	public void setExecutor(String executor) {
		this.executor = executor;
	}

	public String getFullDisplayName() {
		return fullDisplayName;
	}

	public void setFullDisplayName(String fullDisplayName) {
		this.fullDisplayName = fullDisplayName;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Boolean getKeepLog() {
		return keepLog;
	}

	public void setKeepLog(Boolean keepLog) {
		this.keepLog = keepLog;
	}

	public Integer getNumber() {
		return number;
	}

	public void setNumber(Integer number) {
		this.number = number;
	}

	public Integer getQueueId() {
		return queueId;
	}

	public void setQueueId(Integer queueId) {
		this.queueId = queueId;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public Long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Long timestamp) {
		this.timestamp = timestamp;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

}
