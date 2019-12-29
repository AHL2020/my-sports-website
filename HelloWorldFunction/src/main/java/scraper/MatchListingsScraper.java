package scraper;

import beans.MatchBean;
import beans.MatchUrlBean;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import java.net.URL;
import java.io.File;
import org.apache.commons.io.FileUtils;
import util.DataUtils;
import java.net.URLConnection;

public class MatchListingsScraper {

    private Regions REGION = Regions.US_EAST_1;
    private HashMap<String, String> sources = null;
    private HashMap<String, MatchBean> matches = null;
    private String s3bucket = "";
    private String databaseObjectKey = "";
    private String defaultImageName = "";
    private String imagesFolder = "";
    private String pagesFolder = "";
    private boolean scrapeAll = false;

    public static void main(String[] args) {
        MatchListingsScraper scraper = new MatchListingsScraper();

        // configuration: todo: load from S3 file
        scraper.addSource("Bundesliga", "https://footballfullmatch.com/bundesliga/");
        scraper.addSource("PremierLeague", "https://footballfullmatch.com/premier-league/");
        scraper.addSource("LaLiga", "https://footballfullmatch.com/la-liga/");
        scraper.addSource("SerieA", "https://footballfullmatch.com/serie-a/");
        scraper.addSource("Ligue1", "https://footballfullmatch.com/ligue-1/");
        scraper.addSource("ChampionsLeague", "https://footballfullmatch.com/uefa-champions-league/");
        scraper.addSource("EuropaLeague", "https://footballfullmatch.com/uefa-europa-league/");
        scraper.addSource("ECQualification", "https://footballfullmatch.com/ec-qualification/");

        scraper.setDatabaseObjectKey("data/database.csv");
        scraper.setDefaultImageName("default-match-image.jpg");
        scraper.setImagesFolder("images/");
        scraper.setPagesFolder("pages/");
        scraper.setS3bucket("my-sports-website");
        scraper.setScrapeAll(false);

        scraper.process();
    }

    public MatchListingsScraper() {
        sources = new HashMap<String, String>();
        matches = new HashMap<String, MatchBean>();
    }

    public void addSource(String key, String url) {
        sources.put(key, url);
    }

    public void process() {

        int matchesBefore = 0;
        int matchesAfter = 0;

        // Load data from S3
        System.out.println("Load data from S3 ... ");
        matches = DataUtils.loadMatchBeanObjects(s3bucket, databaseObjectKey);
        matchesBefore = matches.size();
        System.out.println("Loaded match data from S3: " + matches.size());

        sources.forEach((key, value) -> parse(key, value));
        System.out.println("Processing completed");

        //System.out.println(matches);

        // insert data csv file into S3
        boolean s3status = DataUtils.saveMatchBeanObjects(matches, s3bucket, databaseObjectKey);
        System.out.println("Inserted data file to S3: " + s3status);

        // report
        matchesAfter = matches.size();
        int added = matchesAfter - matchesBefore;
        System.out.println("Added matches: (" + added + ")");
    }

    public static String getDatePattern() {
        return "EEEE, dd MMMM yyyy";
    }

    public void parse(String competitionIn, String url) {

        System.out.println("Parsing competition: [" + competitionIn + "]");

        try {

            Document doc = Jsoup.connect(url).get();

            Elements articles = doc.select("article");

            System.out.printf("Loaded %d matches from %s from URL: %s.\n", articles.size(), competitionIn, url);

            Pattern regexPattern = null;
            Matcher regexMatcher = null;

            boolean abort = false;

            AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();

            for(int i = 0; i < articles.size() && (!abort || scrapeAll); i++) {

                MatchBean match = new MatchBean();
                Element article = articles.get(i);

                // Match name (OK)
                String matchPairing = article.select("div > section > h3 > a").text().trim();
                //System.out.println("Process: " + matchPairing);
                match.setMatchPairing(matchPairing);

                // League / Competition (OK)
                String[] matchNameParts = matchPairing.split(" vs ");
                String leagueText = article.select("div > section > div > span > a").text().trim();//BUNDESLIGA
                String[] leagueParts = leagueText.split("[0-9]]* comments?");
                String competition = leagueParts[0].trim();
                match.setCompetition(competition);

                // Home team (OK)
                String homeTeam = matchNameParts[0].trim();//Freiburg vs Borussia Dortmund
                match.setHomeTeam(homeTeam);

                // Away team (OK)
                String awayTeam = matchNameParts[1].trim();
                match.setAwayTeam(awayTeam);

                // Year (OK)
                String description = article.select("div > section > p").text().trim();
                String year = "0000/0000";
                regexPattern = Pattern.compile("[0-9]{4}/[0-9]{4}");
                regexMatcher = regexPattern.matcher(description);
                if(regexMatcher.find()) {
                    year = regexMatcher.group();
                }
                match.setYear(year);

                // Match week (OK)
                String defaultMatchWeek = "Week 0";
                String matchWeek = defaultMatchWeek;
                regexPattern = Pattern.compile("Week [0-9]{1,2}");
                regexMatcher = regexPattern.matcher(description);
                if(regexMatcher.find()) {
                    matchWeek = regexMatcher.group();
                }

                // For Champions League and Europa League, look for 'Group A'
                if(matchWeek.equals(defaultMatchWeek)) {
                    regexPattern = Pattern.compile("Group [A-Z]{1}");
                    regexMatcher = regexPattern.matcher(description);
                    if(regexMatcher.find()) {
                        matchWeek = regexMatcher.group();
                    }
                }
                match.setMatchWeek(matchWeek);

                // Date (OK)
                String date = "Day, 00 Month 0000";
                regexPattern = Pattern.compile("((Mon|Tues|Wed(nes)?|Thur(s)?|Fri|Sat(ur)?|Sun)(day)?),\\s[0-9]{1,2}\\s(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|(Nov|Dec)(?:ember)?)\\s[0-9]{4}");
                regexMatcher = regexPattern.matcher(description);
                if(regexMatcher.find()) {
                    date = regexMatcher.group();
                }
                match.setDate(date);

                // URL to the actual match details
                Elements matchUrlLinks = article.select("div > section > h3 > a");
                Element matchUrlLink = matchUrlLinks.first();
                String matchUrlString = matchUrlLink.attr("abs:href");
                match.setMatchUrlString(matchUrlString);

                // fetch the URLs to the media files
                Document matchPage = Jsoup.connect(matchUrlString).get();
                String matchPageHtml = matchPage.html();
                List<MatchUrlBean> matchUrls = extractMatchUrlAndLabels(matchPageHtml, false);
                String matchUrlsString = matchUrls.toString();
                match.setMatchUrls(matchUrlsString);


                // Image URL (use element selector)
                Elements imageUrlElements = null;
                Element imageUrlElement = null;
                imageUrlElements = article.select("div > div > a");
                imageUrlElement = imageUrlElements.get(0);
                String imageUrlElementHtml = imageUrlElement.html();
                String imageUrl = "";
                if(imageUrlElementHtml.indexOf(".jpg") != -1) {
                    // .jpg
                    int p2 = imageUrlElementHtml.indexOf(".jpg");
                    imageUrl = imageUrlElementHtml.substring(0, p2+4);
                    int p1 = imageUrl.lastIndexOf("http");
                    imageUrl = imageUrl.substring(p1);
                } else if(imageUrlElementHtml.indexOf(".png") != -1) {
                    // .png
                    int p2 = imageUrlElementHtml.indexOf(".png");
                    imageUrl = imageUrlElementHtml.substring(0, p2+4);
                    int p1 = imageUrl.lastIndexOf("http");
                    imageUrl = imageUrl.substring(p1);
                } else {

                }

                // Image name
                int beginIndex = imageUrl.lastIndexOf("/") + 1;
                String imageName = imageUrl.substring(beginIndex);
                String objectKey = imagesFolder + competitionIn + "/" + imageName;
                if(!isValidImageName(imageName)) {
                    imageName = imagesFolder + defaultImageName;
                } else {
                    imageName = imagesFolder + competitionIn + "/" + imageName;
                }
                match.setImageName(imageName);

                // Match key
                int index = matchUrlString.lastIndexOf("/");
                String matchKey = matchUrlString.substring(0, index);
                index = matchKey.lastIndexOf("/");
                matchKey = matchKey.substring(index+1);
                match.setMatchKey(matchKey);

                // page Url
                String linkUrl = pagesFolder + competitionIn + "/" + match.getMatchKey() + ".html";
                match.setLinkUrl(linkUrl);

                // Set dates
                //match.setPageCreatedDate(null);
                //match.setPageLastUpdatedDate(null);
                if(match.getScrapedDate() == null) {
                    match.setScrapedDate(Instant.now());
                }
                //System.out.println("Scraped date: " + match.getScrapedDate());
                //System.out.println("Created date: " + match.getPageCreatedDate());
                //System.out.println("Updated date: " + match.getPageLastUpdatedDate());

                //System.out.println(match);

                // Add match if new
                if(matches.containsKey(matchKey)) {
                    //System.out.println("Match already exists in database.");
                    abort = true;
                    if(scrapeAll) {
                        //System.out.println("scrapeAll = true. Add match and continue scraping ...");
                        matches.put(matchKey, match);
                    } else if(abort) {
                        //System.out.println("Skip parsing rest of matches.");
                    }
                } else {
                    matches.put(matchKey, match);
                    //System.out.println("Added: " + matchKey);
                }

                // Insert image into S3
                //System.out.print("Insert [" + objectKey + "] ? ");
                if(isValidImageName(imageName) && (!abort || scrapeAll)) {
                    //System.out.println("yes");

        // ======================================================
        //                     Lambda Deployment
        // ======================================================
                    File fileToPut = new File(imageName);
                    //File fileToPut = new File("/tmp/" + imageName); // for lambda deployment

                    URLConnection conn = new URL(imageUrl).openConnection();
                    conn.connect();
                    InputStream inputStream = conn.getInputStream();
                    FileUtils.copyInputStreamToFile(inputStream, fileToPut);
                    String contentType = "";
                    String fileExtension = imageName.substring(imageName.lastIndexOf(".") + 1);
                    if (fileExtension.equals("jpg")) contentType = "image/jpeg";
                    if (fileExtension.equals("jpeg")) contentType = "image/jpeg";
                    if (fileExtension.equals("png")) contentType = "image/png";

                    ObjectMetadata metaData = new ObjectMetadata();
                    metaData.setContentType(contentType);

                    PutObjectRequest putObjReq =
                            new PutObjectRequest(s3bucket, objectKey, fileToPut)
                                    .withCannedAcl(CannedAccessControlList.PublicRead);
                    putObjReq.setMetadata(metaData);

                    try {
                        if (!s3.doesObjectExist(s3bucket, objectKey)) {
                            s3.putObject(putObjReq);
                            //System.out.println("Inserted image for match: " + objectKey);
                        } else {
                            //System.out.println("Image already exists: " + objectKey);
                        }
                    } catch(Exception e) {
                        System.out.println("Error inserting image for match: " + objectKey);
                        //e.printStackTrace();
                    }
                    fileToPut.delete(); // delete the files that are stored locally

                    //System.out.println(match.getImageName());
                } else {
                    //System.out.println("no");
                }


            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public String getS3bucket() {
        return s3bucket;
    }

    public void setS3bucket(String s3bucket) {
        this.s3bucket = s3bucket;
    }

    public String getDatabaseObjectKey() {
        return databaseObjectKey;
    }

    public void setDatabaseObjectKey(String databaseObjectKey) {
        this.databaseObjectKey = databaseObjectKey;
    }

    public String getDefaultImageName() {
        return defaultImageName;
    }

    public void setDefaultImageName(String defaultImageName) {
        this.defaultImageName = defaultImageName;
    }

    public String getImagesFolder() {
        return imagesFolder;
    }

    public void setImagesFolder(String imagesFolder) {
        this.imagesFolder = imagesFolder;
    }

    public String getPagesFolder() {
        return pagesFolder;
    }

    public void setPagesFolder(String pagesFolder) {
        this.pagesFolder = pagesFolder;
    }

    public boolean isScrapeAll() {
        return scrapeAll;
    }

    public void setScrapeAll(boolean scrapeAll) {
        this.scrapeAll = scrapeAll;
    }

    // Utility Methods

    public static boolean isValidImageName(String imageName) {
        if(imageName.contains("-vs-")) return true;
        return false;
    }

    public static List<String> extractURLs(String html, boolean queryString) {
        String urlRegex = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern pattern = Pattern.compile(urlRegex);
        Matcher matcher = pattern.matcher(html);
        List<String> urls = new ArrayList<String>();
        while (matcher.find()) {
            String url = matcher.group();
            if(!queryString) {
                int position = url.indexOf("?");
                if(position != -1) {
                    url = url.substring(0, position);
                }
            }
            //System.out.println(url);
            urls.add(url);
        }
        return urls;
    }

    public static List<String> extractURLs(String html) {
        return extractURLs(html, true);
    }

    public static List<MatchUrlBean> extractMatchUrlAndLabels(String html, boolean queryString) {
        List<MatchUrlBean> list = new ArrayList<MatchUrlBean>();
        List<String> urls = extractMediaLinks(html);
        List<String> labels = extractMatchUrlLabels(html);
        for(int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String label = labels.get(i);
            list.add(new MatchUrlBean(url, label));
        }
        return list;
    }

    public static List<String> extractMatchUrls(String html, boolean queryString) {
        Document doc = Jsoup.parse(html);
        Elements tmpElements = doc.select("div.entry-content > script");
        Element tmpElement = tmpElements.first();

        String elementHtml = tmpElement.html();
        String urlRegex = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern pattern = Pattern.compile(urlRegex);
        Matcher matcher = pattern.matcher(elementHtml);
        List<String> urls = new ArrayList<String>();
        while (matcher.find()) {
            String url = matcher.group();
            if(!queryString) {
                int position = url.indexOf("?");
                if(position != -1) {
                    url = url.substring(0, position);
                }
            }
            urls.add(url);
        }
        return urls;
    }

    public static List<String> extractMatchUrls(String html) {
        return extractMatchUrls(html, true);
    }

    public static List<String> extractMatchUrlLabels(String html) {
        List<String> labels = new ArrayList<String>();
        Document doc = Jsoup.parse(html);
        Elements tmpElements = doc.select("div#vid-control");
        Element tmpElement = tmpElements.first();
        tmpElements = tmpElement.select("li");
        for(Element e: tmpElements) {
            labels.add(e.text());
        }
        return labels;
    }

    public static LinkedList<String> extractMediaLinks(String html) {
        LinkedList<String> list = new LinkedList<String>();
        if(html == null) return list;
        if(html.trim().equalsIgnoreCase("")) return list;
        int index1 = 0;
        int index2 = 0;
        String extract = "";
        String token = "";
        index1 = html.indexOf("videoSelector");
        index2 = html.indexOf("]", index1);
        index1 = html.indexOf("[", index1);
        extract = html.substring(index1, index2);
        while(extract.contains("http")) {
            index1 = extract.indexOf("http");
            index2 = extract.indexOf("\"", index1) - 1;
            token = extract.substring(index1, index2);
            token = token.replaceAll("\\\\", "");
            list.add(token);
            extract = extract.substring(index2);
        }
        return list;
    }
}
