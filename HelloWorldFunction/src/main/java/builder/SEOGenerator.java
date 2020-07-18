package builder;

import beans.MatchBean;
import cz.jiripinkas.jsitemapgenerator.WebPage;
import cz.jiripinkas.jsitemapgenerator.generator.RssGenerator;
import cz.jiripinkas.jsitemapgenerator.generator.SitemapGenerator;
import cz.jiripinkas.jsitemapgenerator.robots.RobotsRule;
import cz.jiripinkas.jsitemapgenerator.robots.RobotsTxtGenerator;
import org.apache.commons.text.StringEscapeUtils;
import util.DataUtils;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.text.Normalizer;


public class SEOGenerator {
    private String sitemap = "";
    private String rssfeed = "";
    private String robots = "";
    private String websiteUrl = "";
    private String webTitle = "";
    private String webDescr = "";
    private String relativePath = "";
    private List<String> pages = null;
    private List<WebPage> feeds = null;
    private int feedLength = 0;

    public static void main(String[] args) {
        try {

            String bucketName = "my-sports-website";
            String databaseKey = "data/database.csv";
            HashMap<String, MatchBean> matches = DataUtils.loadMatchBeanObjects(bucketName, databaseKey);
            LinkedList<MatchBean> matchesLL = DataUtils.convertHMtoLL(matches);

            String websiteUrl = "https://europefootball.net";
            String webTitle = "EuropeFootball.net";
            String webDescr = "Watch full match replays and highlights of Premier League, Champions League, La Liga, Bundesliga, Serie A, Ligue 1 and Europa League";

            SEOGenerator seo = new SEOGenerator(websiteUrl, webTitle, webDescr, 50);
            seo.process(matchesLL);

            String sitemap = seo.getSitemap();
            String rssfeed = seo.getRssfeed();
            String robots = seo.getRobots();

            System.out.println("sitemap: " + sitemap);
            System.out.println("rssfeed: " + rssfeed);
            System.out.println("robots: " + robots);

            DataUtils.saveContentToS3(bucketName, "sitemap.xml", sitemap, "text/xml");
            DataUtils.saveContentToS3(bucketName, "rssfeed.xml", rssfeed, "text/xml");
            DataUtils.saveContentToS3(bucketName, "robots.txt", robots, "text/plain");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public SEOGenerator() {
    }

    public SEOGenerator(String websiteUrl, String webTitle, String webDescr, int feedLength) {
        this.websiteUrl = websiteUrl;
        this.webTitle = webTitle;
        this.webDescr = webDescr;
        this.pages = new LinkedList<String>();
        this.feeds = new LinkedList<WebPage>();
        this.feedLength = feedLength;
    }

    public boolean process(LinkedList<MatchBean> matches) {

        String relativePath = "";
        String fileName = "";

        Collections.sort(matches);

        addPage("index.html", "");
        addPage("About.html", "");

        for (int i = 0; i < matches.size(); i++) {

            MatchBean mb = matches.get(i);
            fileName = mb.getMatchKey() + ".html";
            relativePath = "pages" + "/" + mb.getCompetition().replaceAll(" ", "").replaceAll("UEFA", "");
            addPage(fileName, relativePath);

            if(i < feedLength) {
                Date d = DataUtils.convertStringToDate(mb.getDate());
                LocalDateTime timestamp = null; //d.toInstant()
                //.atZone(ZoneId.systemDefault())
                //.toLocalDateTime();
                if (mb.getPageCreatedDate() != null) {
                    timestamp = d.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                } else {
                    timestamp = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                }

                //String escapedTitle = StringEscapeUtils.escapeHtml4(mb.getMatchPairing());
                String escapedTitle = Normalizer.normalize(mb.getMatchPairing(), Normalizer.Form.NFD);
                escapedTitle = escapedTitle.replaceAll("[^\\p{ASCII}]", "");
                escapedTitle = escapedTitle.replaceAll("&", "&amp;");

                        addFeed(WebPage.rssBuilder()
                        .pubDate(timestamp)
                        .title(escapedTitle)
                        //.description(mb.getCompetition() + " - " + mb.getMatchWeek() + " - " + mb.getDate())
                        .description(mb.getCompetition() + " - " + mb.getDate())
                        .link(relativePath + "/" + fileName)
                        .build());
            }
        }

        return true;
    }

    public boolean addPage(String page, String relativePath) {
        return this.pages.add(relativePath + "/" + page);
    }

    public boolean addFeed(WebPage feed) {
        return this.feeds.add(feed);
    }

    public String getSitemap() {
        sitemap = SitemapGenerator.of(websiteUrl)
                .addPage(WebPage.builder().nameRoot().priorityMax().build())
                //.defaultDir(relativePath)
                .addPages(pages, page -> WebPage.of(page))
                .toString();
        return sitemap;
    }

    public String getRssfeed() {
        rssfeed = RssGenerator.of(websiteUrl, webTitle, webDescr)
                .addPages(feeds)
                .toString();
        //rssfeed = rssfeed.replace("UTF-8", "ISO-8859-1");
        return rssfeed;
    }

    public String getRobots() {
        robots = RobotsTxtGenerator.of(websiteUrl)
                .addSitemap("sitemap.xml")
                .addRule(RobotsRule.builder().userAgentAll().allowAll().build())
                .toString();
        return robots;
    }
    public void setSitemap(String sitemap) {
        this.sitemap = sitemap;
    }

    public void setRssfeed(String rssfeed) {
        this.rssfeed = rssfeed;
    }

    public void setRobots(String robots) {
        this.robots = robots;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public void setWebsiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl;
    }
}
