package beans;
public class MatchUrlBean {
	private String matchUrl;
	private String matchUrlLabel;
	public MatchUrlBean() {
		this.matchUrl = "";
		this.matchUrlLabel = "";
	}
	public MatchUrlBean(String matchUrl, String matchUrlLabel) {
		this.matchUrl = matchUrl;
		this.matchUrlLabel = matchUrlLabel;
	}
	public String getMatchUrl() {
		return matchUrl;
	}
	public void setMatchUrl(String matchUrl) {
		this.matchUrl = matchUrl;
	}
	public String getMatchUrlLabel() {
		return matchUrlLabel;
	}
	public void setMatchUrlLabel(String matchUrlLabel) {
		this.matchUrlLabel = matchUrlLabel;
	}
	public String toString() {
		return "" + matchUrl + "," + matchUrlLabel + "";
	}
}