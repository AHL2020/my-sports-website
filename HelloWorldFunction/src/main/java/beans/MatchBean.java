package beans;

import util.DataUtils;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedList;

//@DynamoDBTable(tableName = "Bundesliga")
public class MatchBean implements Comparable<MatchBean> {
    private String matchKey;
    private String matchPairing;
    private String matchWeek;
    private String homeTeam;
    private String awayTeam;
    private String competition;
    private String date;
    private String imageName;
    private String matchUrlString;
    private String year;
    private String matchUrls;
    private LinkedList<MatchUrlBean> matchUrlsAndTags;
    private String linkUrl;

    private Instant scrapedDate;
    private Instant pageCreatedDate;
    private Instant pageLastUpdatedDate;

    public MatchBean() {
        //
    }

    public MatchBean(String matchKey, String matchPairing, String matchWeek, String homeTeam, String awayTeam,
                     String competition, String date, String imageName, String matchUrlString, String year,
                     String matchUrls, LinkedList<MatchUrlBean> matchUrlsAndTags, String linkUrl,
                     Instant scrapedDate, Instant pageCreatedDate, Instant pageLastUpdatedDate) {
        this.matchKey = matchKey;
        this.matchPairing = matchPairing;
        this.matchWeek = matchWeek;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.competition = competition;
        this.date = date;
        this.imageName = imageName;
        this.matchUrlString = matchUrlString;
        this.year = year;
        this.matchUrls = matchUrls;
        this.matchUrlsAndTags = matchUrlsAndTags;
        this.linkUrl = linkUrl;
        this.scrapedDate = scrapedDate;
        this.pageCreatedDate = pageCreatedDate;
        this.pageLastUpdatedDate = pageLastUpdatedDate;
    }

    //@Override
    public String toString() {
        return "MatchBean{" +
                "matchKey='" + matchKey + '\'' +
                ", matchPairing='" + matchPairing + '\'' +
                ", matchWeek='" + matchWeek + '\'' +
                ", homeTeam='" + homeTeam + '\'' +
                ", awayTeam='" + awayTeam + '\'' +
                ", competition='" + competition + '\'' +
                ", date='" + date + '\'' +
                ", imageName='" + imageName + '\'' +
                ", matchUrlString='" + matchUrlString + '\'' +
                ", year='" + year + '\'' +
                ", matchUrls='" + matchUrls + '\'' +
                ", matchUrlsAndTags='" + matchUrlsAndTags + '\'' +
                ", linkUrl='" + linkUrl + '\'' +
                ", scrapedDate='" + scrapedDate + '\'' +
                ", pageCreatedDate='" + pageCreatedDate + '\'' +
                ", pageLastUpdatedDate='" + pageLastUpdatedDate + '\'' +
                '}';
    }

    public String toCsvString() {
        return "\"" + matchKey + "\"" +
                ",\"" + matchPairing + "\"" +
                ",\"" + matchWeek + "\"" +
                ",\"" + homeTeam + "\"" +
                ",\"" + awayTeam + "\"" +
                ",\"" + competition + "\"" +
                ",\"" + date + "\"" +
                ",\"" + imageName + "\"" +
                ",\"" + matchUrlString + "\"" +
                ",\"" + year + "\"" +
                ",\"" + matchUrls + "\"" +
                ",\"" + matchUrlsAndTags + "\"" +
                ",\"" + linkUrl + "\"" +
                ",\"" + scrapedDate + "\"" +
                ",\"" + pageCreatedDate + "\"" +
                ",\"" + pageLastUpdatedDate + "\"";
    }

    //@DynamoDBHashKey(attributeName = "MatchPairing")
    public String getMatchPairing() {
        return matchPairing;
    }

    public void setMatchPairing(String matchPairing) {
        this.matchPairing = matchPairing;
    }

    //@DynamoDBRangeKey(attributeName = "MatchWeek")
    public String getMatchWeek() {
        return matchWeek;
    }

    public void setMatchWeek(String matchWeek) {
        this.matchWeek = matchWeek;
    }

    //@DynamoDBAttribute(attributeName = "MatchKey")
    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    //@DynamoDBAttribute(attributeName = "HomeTeam")
    public String getHomeTeam() {
        return homeTeam;
    }

    public void setHomeTeam(String homeTeam) {
        this.homeTeam = homeTeam;
    }

    //@DynamoDBAttribute(attributeName = "AwayTeam")
    public String getAwayTeam() {
        return awayTeam;
    }

    public void setAwayTeam(String awayTeam) {
        this.awayTeam = awayTeam;
    }

    //@DynamoDBAttribute(attributeName = "Competition")
    public String getCompetition() {
        return competition;
    }

    public void setCompetition(String competition) {
        this.competition = competition;
    }

    //@DynamoDBAttribute(attributeName = "Date")
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    //@DynamoDBAttribute(attributeName = "Year")
    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    //@DynamoDBAttribute(attributeName = "ImageName")
    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    //@DynamoDBAttribute(attributeName = "MatchUrlString")
    public String getMatchUrlString() {
        return matchUrlString;
    }

    public void setMatchUrlString(String matchUrlString) {
        this.matchUrlString = matchUrlString;
    }

    //@DynamoDBAttribute(attributeName = "MatchUrls")
    public String getMatchUrls() {
        return matchUrls;
    }

    public void setMatchUrls(String matchUrls) {
        this.matchUrls = matchUrls;
    }

    //@DynamoDBAttribute(attributeName = "LinkUrl")
    public String getLinkUrl() {
        return linkUrl;
    }

    public void setLinkUrl(String linkUrl) {
        this.linkUrl = linkUrl;
    }

    //@DynamoDBIgnore
    public LinkedList<MatchUrlBean> getMatchUrlsAndTags() {
        return matchUrlsAndTags;
    }

    public void setMatchUrlsAndTags(LinkedList<MatchUrlBean> matchUrlsAndTags) {
        this.matchUrlsAndTags = matchUrlsAndTags;
    }

    public Instant getScrapedDate() {
        return scrapedDate;
    }

    public void setScrapedDate(Instant scrapedDate) {
        this.scrapedDate = scrapedDate;
    }

    public Instant getPageCreatedDate() {
        return pageCreatedDate;
    }

    public void setPageCreatedDate(Instant pageCreatedDate) {
        this.pageCreatedDate = pageCreatedDate;
    }

    public Instant getPageLastUpdatedDate() {
        return pageLastUpdatedDate;
    }

    public void setPageLastUpdatedDate(Instant pageLastUpdatedDate) {
        this.pageLastUpdatedDate = pageLastUpdatedDate;
    }

    public boolean isValid() {
        if(this.matchKey == null) return false;
        if(this.matchKey.trim().equalsIgnoreCase("")) return false;
        if(this.imageName == null) return false;
        if(this.imageName.trim().equalsIgnoreCase("")) return false;
        if(this.linkUrl == null) return false;
        if(this.linkUrl.trim().equalsIgnoreCase("")) return false;
        return true;
    }

    public int compareTo(MatchBean match) {

        try {
            Instant d1 = match.getScrapedDate();
            Instant d2 = this.getScrapedDate();
            return d1.compareTo(d2);
        } catch(Exception e) {
            e.printStackTrace();
        }

        Date d1 = DataUtils.convertStringToDate(match.getDate());
        Date d2 = DataUtils.convertStringToDate(this.getDate());
        return d1.compareTo(d2);

    }
}
