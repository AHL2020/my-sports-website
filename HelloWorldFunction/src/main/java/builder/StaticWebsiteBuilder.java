package builder;

import beans.MatchUrlBean;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import shared.ContentTypeManager;
import util.DataUtils;
import beans.MatchBean;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.Template;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class StaticWebsiteBuilder {

    private HashMap<String, MatchBean> matchesHM = null;
    private String s3bucket = "";
    private String databaseObjectKey = "";
    private String imagesFolder = "";
    private String pagesFolder = "";
    private String templatesFolder = "";
    private boolean overwrite = false;
    private String freemarkerTemplateDirectoryUrl = "";
    private final AmazonS3 s3;
    HashMap<String, String> sources;
    private int pagesCreated = 0;
    private int matchesPerPage = 10;

    public static int CLOUD = 0;
    public static int LOCAL = 1;
    private final int deploymentType;

    public static void main(String[] args) {

        StaticWebsiteBuilder builder = new StaticWebsiteBuilder(StaticWebsiteBuilder.LOCAL);

        builder.addSource("Bundesliga", "bundesliga");
        builder.addSource("PremierLeague", "premierLeague");
        builder.addSource("LaLiga", "laLiga");
        builder.addSource("SerieA", "serieA");
        builder.addSource("Ligue1", "ligue1");
        builder.addSource("ChampionsLeague", "championsLeague");
        builder.addSource("EuropaLeague", "europaLeague");
        builder.addSource("ECQualification", "euro2020");
        builder.addSource("Misc", "misc");

        builder.setS3bucket("my-sports-website");
        builder.setDatabaseObjectKey("data/database.csv");
        builder.setImagesFolder("images/");
        builder.setPagesFolder("pages/");
        builder.setTemplatesFolder("templates/");
        builder.setOverwrite(false);
        builder.setMatchesPerPage(10);
        builder.setFreemarkerTemplateDirectoryUrl("https://my-sports-website.s3.amazonaws.com");

        builder.build();

        String websiteUrl = "https://europefootball.net";
        String webTitle = "EuropeFootball.net";
        String webDescr = "Watch full match replays and highlights of Premier League, Champions League, La Liga, Bundesliga, Serie A, Ligue 1 and Europa League";

        builder.generateSEO(websiteUrl, webTitle, webDescr);
    }

    public StaticWebsiteBuilder(int deploymentType) {
        if (deploymentType < 0 || deploymentType > 1) this.deploymentType = StaticWebsiteBuilder.LOCAL;
        else this.deploymentType = deploymentType;
        System.out.println("[StaticWebsiteBuilder] running in [" + getDeploymentTypeName() + "] mode.");
        Regions REGION = Regions.US_EAST_1;
        s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();
        sources = new HashMap<>();
    }

    public String getDeploymentTypeName() {
        if (this.deploymentType == StaticWebsiteBuilder.CLOUD) return "CLOUD";
        else return "LOCAL";
    }

    public void addSource(String nameKey, String templateKey) {
        sources.put(nameKey, templateKey);
    }

    public void generateSEO(String websiteUrl, String webTitle, String webDescr) {
        System.out.println("Generating SEO ...");
        if (matchesHM == null) return;
        SEOGenerator seo = new SEOGenerator(websiteUrl, webTitle, webDescr, 15);
        //--
        sources.forEach((key, value) -> seo.addPage(key + ".html", ""));
        seo.addPage("Contact.html", "");
        seo.addPage("About.html", "");
        seo.addPage("index.html", "");
        //--
        boolean result = seo.process(DataUtils.convertHMtoLL(matchesHM));
        if(result) {
            String sitemap = seo.getSitemap();
            String rssfeed = seo.getRssfeed();
            String robots = seo.getRobots();
            DataUtils.saveContentToS3(s3bucket, "sitemap.xml", sitemap, "text/xml");
            DataUtils.saveContentToS3(s3bucket, "rssfeed.xml", rssfeed, "text/xml");
            DataUtils.saveContentToS3(s3bucket, "robots.txt", robots, "text/plain");
        }
    }

    public void build() {

        try {
            // Load data from S3
            System.out.println("Load data from S3 ... ");
            matchesHM = DataUtils.loadMatchBeanObjects(s3bucket, databaseObjectKey);
            System.out.println("Loaded match data from S3: " + matchesHM.size());

            // data structure to store all matches data
            HashMap<String, List<MatchBean>> tmpAllData = new HashMap<>();

            // list of all matches -> for randoms etc
            LinkedList<MatchBean> allMatches = new LinkedList<>();

            // the data model that will be processed by Free Marker into the template
            Map<String, HashMap<String, List<MatchBean>>> dataModel = new HashMap<>();

            System.out.println("loading data ...");

            // process each league
            sources.forEach((key, value) -> {
                List<MatchBean> tmpList = getAllMatchesByCompetition(key);
                LinkedList<MatchBean> tmpLL = DataUtils.convertPSLtoLL(tmpList);
                Collections.sort(tmpLL);
                // set correct image and page location URLs
                for (MatchBean match : tmpLL) {
                    allMatches.add(match);
                    String relativeLinkUrl = pagesFolder + key + "/" + match.getMatchKey() + ".html";
                    match.setLinkUrl(relativeLinkUrl);
                }
                // store all matches in temporary data structure
                tmpAllData.put(key, tmpLL);
            });

            Collections.sort(allMatches);

            // create homepage
            System.out.println("create homepage ...");

            sources.forEach((key, value) -> {
                dataModel.put(value, getDataModelForHomepageByCompetition(key, tmpAllData.get(key)));
                // set path
                dataModel.put("path2", DataUtils.makePath(""));
            });

            HashMap<String, List<MatchBean>> tmpHashMap = new HashMap<>();
            tmpHashMap.put("mostRecent", allMatches);
            dataModel.put("allMatches", tmpHashMap);

            String htmlFileName = "index.html";
            String relativePath = "";
            String templateName = templatesFolder + "homepage.ftlh";

            // always overwrite homepage
            createPage(dataModel, htmlFileName, relativePath, templateName, true);

            // create 'About' page
            System.out.println("create 'About' page ...");
            htmlFileName = "About.html";
            relativePath = "";
            templateName = templatesFolder + "about.ftlh";
            createPage(dataModel, htmlFileName, relativePath, templateName, true);

            // create 'Contact' page
            System.out.println("create 'Contact' page ...");
            htmlFileName = "Contact.html";
            relativePath = "";
            templateName = templatesFolder + "contact.ftlh";
            createPage(dataModel, htmlFileName, relativePath, templateName, true);

            // create category pages
            // always overwrite category pages
            System.out.println("create category pages ...");
            templateName = templatesFolder + "category.ftlh";
            for (Map.Entry<String, String> league : sources.entrySet()) {

                // get number of matches in the category
                int matchesCount = getAllMatchesByCompetition(league.getKey()).size();
                //System.out.println("Competition: " + league.getKey());
                //System.out.println("Matches: " + matchesCount);
                // div it by e.g. 10 -> this gives us how many pages there are
                double pagesD = Math.ceil((1.0 * matchesCount) / matchesPerPage);
                int pages = (int) pagesD;

                // how many max. buttons in the bottom page navigation
                int navSize = 5;
                if (navSize > pages) navSize = pages;

                //System.out.println("Matches p. page: " + matchesPerPage);
                //System.out.println("Pages to create: " + pages);
                // for each page (i):
                for (int i = 0; i < pages; i++) {
                    // get/set data model for the i-th page
                    int indexBegin = matchesPerPage * i;
                    int indexEnd = Math.min((matchesPerPage * (i + 1)), matchesCount);
                    //System.out.println("indexBegin: " + indexBegin);
                    //System.out.println("indexEnd: " + indexEnd);
                    dataModel.put("matches", getDataModelForCategory(league.getKey(), tmpAllData, indexBegin, indexEnd));

                    // make and set the navigation
                    dataModel.put("navigation", getDataModelForNav(league.getKey(), ".html", i, navSize, pages));

                    // set path
                    dataModel.put("path2", DataUtils.makePath(""));

                    String TEST = ""; // todo: only for testing: "_TEST_";

                    // make the filename
                    if (i == 0) {
                        htmlFileName = league.getKey() + TEST + ".html";
                    } else {
                        htmlFileName = league.getKey() + "-" + i + TEST + ".html";
                    }

                    //set competition key
                    dataModel.put("competitionKey", DataUtils.makeCompetitionKey(league.getKey()));

                    // create the page
                    createPage(dataModel, htmlFileName, relativePath, templateName, true);
                }
            }

            // create match pages

            System.out.println("create match pages ...");
            // set path
            dataModel.put("path2", DataUtils.makePath("../../"));
            templateName = templatesFolder + "match.ftlh";
            for (String competition : tmpAllData.keySet()) {
                LinkedList<MatchBean> matches = (LinkedList<MatchBean>) tmpAllData.get(competition);
                Collections.sort(matches);
                for(MatchBean match : matches) {
                    boolean createPage = (match.getPageCreatedDate() == null) || overwrite;
                    if (createPage && match.isValid()) {
                        if (match.getPageCreatedDate() == null) {
                            match.setPageCreatedDate(Instant.now());
                        }
                        match.setPageLastUpdatedDate(Instant.now());
                        match.setMatchUrlsAndTags(makeMatchUrlBean(match.getMatchUrls()));
                        dataModel.put("match", getDataModelForMatch(match));

                        relativePath = pagesFolder + competition + "/";
                        //set competition key
                        //System.out.println(competition);
                        dataModel.put("competitionKey", DataUtils.makeCompetitionKey(competition));

                        // make the menu for the video links
                        dataModel.put("videolinknav", getDataModelForVideoLinkNav(match));
                        // loop over each video link
                        List<MatchUrlBean> videoLinks = match.getMatchUrlsAndTags();
                        System.out.println("[SWB][build] videoLinks: " + videoLinks);
                        int pageCounter = 0;
                        // for each video link, do:
                        for (MatchUrlBean videoLink : videoLinks) {
                            // 1. detect the video player type
                            String videoLinkStr = videoLink.getMatchUrl();
                            System.out.println("[SWB][build] videoLinkStr: " + videoLinkStr);
                            String videoPlayer = ContentTypeManager.getVideoPlayer(videoLinkStr);
                            System.out.println("[SWB][build] videoPlayer: " + videoPlayer);
                            // 2. set the video player type to the data model
                            dataModel.put("videoplayer", getDataModelForVideoPlayer(videoLinkStr, videoPlayer));
                            // 3. update the match page name
                            System.out.println("[SWB][build] pageCounter: " + pageCounter);
                            if (pageCounter > 0) {
                                htmlFileName = match.getMatchKey() + "-" + pageCounter + ".html";
                            } else {
                                htmlFileName = match.getMatchKey() + ".html";
                            }
                            System.out.println("[SWB][build] htmlFileName: " + htmlFileName);
                            createPage(dataModel, htmlFileName, relativePath, templateName, overwrite);
                            //System.out.print(".");
                            pageCounter++;
                        }
                    }
                }
            }

            System.out.println("Created pages: (" + pagesCreated + ")");

            boolean s3status = DataUtils.saveMatchBeanObjects(matchesHM, s3bucket, databaseObjectKey);
            System.out.println("Inserted data file to S3: " + s3status);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, List<MatchBean>> getDataModelForVideoLinkNav(MatchBean match) {
        HashMap<String, List<MatchBean>> dataModel = new HashMap<>();
        List<MatchBean> videoLinkNav = new LinkedList<>();
        List<MatchUrlBean> videoLinks = match.getMatchUrlsAndTags();
        int counter = 0;
        for (MatchUrlBean videoLink : videoLinks) {
            MatchBean mb = new MatchBean();
            String url = match.getMatchKey();
            if (counter == 0) {
                url = url + ".html";
            } else {
                url = url + "-" + counter + ".html";
            }
            mb.setMatchKey(url); // url
            mb.setCompetition(videoLink.getMatchUrlLabel()); // label
            videoLinkNav.add(mb);
            counter++;
        }
        dataModel.put("videolinknav", videoLinkNav);
        System.out.println("[SWB][getDataModelForVideoLinkNav] dataModel: " + dataModel);
        return dataModel;
    }

    private HashMap<String, List<MatchBean>> getDataModelForVideoPlayer(String videoLink, String videoPlayer) {
        HashMap<String, List<MatchBean>> dataModel = new HashMap<>();
        List<MatchBean> matches = new LinkedList<>();
        MatchBean mb = new MatchBean();

        // for Vooplayer, we need to extract the player ID
        //String voo = "voo";
        if (videoPlayer.equals("voo")) {
            videoLink = videoLink.substring(videoLink.lastIndexOf("/") + 1);
        }

        // for Bridplayer, extract the Id
        String bridTag = "bridplayer-id:";
        if (videoPlayer.equals("brid")) {
            videoLink = videoLink.substring(bridTag.length());
        }

        mb.setCompetition(videoLink);
        mb.setMatchKey(videoPlayer);
        matches.add(mb);
        dataModel.put("videoplayer", matches);
        return dataModel;
    }

    private HashMap<String, List<MatchBean>> getDataModelForNav(String pageKey, String fileExt, int currentPage, int navSize, int lastPage) {

        //System.out.println("lastPage: " + lastPage);
        //System.exit(0);

        HashMap<String, List<MatchBean>> dataModel = new HashMap<>();
        List<MatchBean> navList = new LinkedList<>();

        if (fileExt.indexOf(".") != 0) {
            fileExt = "." + fileExt;
        }

        // determine start page
        int startPage;
        if (currentPage >= (lastPage - navSize / 2)) {
            startPage = lastPage - navSize;
        } else if (currentPage > navSize / 2) {
            startPage = currentPage - (navSize / 2);
        } else {
            startPage = 0;
        }

        // make the navigation
        for (int i = 0; i < navSize; i++) {

            MatchBean mb = new MatchBean();

            int page = startPage + i;

            // make links
            String fileName = pageKey;
            if (page == 0) {
                fileName += fileExt;
            } else {
                fileName += "-" + page + fileExt;
            }
            mb.setMatchKey(fileName);

            // set marker for active page
            if (page == currentPage) {
                mb.setMatchPairing("active");
            }

            // set previous, next, page-number labels
            String label;
            if (i == 0 && startPage > 0) {
                label = "<<";
            } else if (i == (navSize - 1) && page <= lastPage - navSize / 2) {
                label = ">>";
            } else {
                label = "" + (page + 1);
            }
            mb.setCompetition(label);

            navList.add(mb);
        }

        dataModel.put("links", navList);

        return dataModel;
    }

    public void setFreemarkerTemplateDirectoryUrl(String freemarkerTemplateDirectoryUrl) {
        this.freemarkerTemplateDirectoryUrl = freemarkerTemplateDirectoryUrl;
    }

    private boolean createPage(Map<String, HashMap<String, List<MatchBean>>> dataModel, String htmlFileName, String relativePath, String templateName, boolean overwrite) {

        try {
            //System.out.println("createPage: htmlFileName:[" + htmlFileName + "], overwrite: [" + overwrite + "]");

            // save the html file to S3
            String contentType = "text/html";
            String objectKey = relativePath + htmlFileName;

            Configuration cfg = new Configuration(Configuration.VERSION_2_3_29);
            cfg.clearTemplateCache();
            cfg.setTemplateLoader(new S3TemplateLoader(new URL(freemarkerTemplateDirectoryUrl)));
            cfg.setDefaultEncoding("UTF-8");
            cfg.setLocalizedLookup(false);
            cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            cfg.setLogTemplateExceptions(false);
            cfg.setWrapUncheckedExceptions(true);
            cfg.setFallbackOnNullLoopVariable(false);
            cfg.removeTemplateFromCache(templateName, "UTF-8");
            cfg.removeTemplateFromCache(templateName, Locale.getDefault());
            cfg.clearTemplateCache();
            Template template = cfg.getTemplate(templateName);

            // ======================================================
            //                     Lambda Deployment
            // ======================================================

            File outHtml;
            if (this.deploymentType == StaticWebsiteBuilder.LOCAL) {
                // for local deployment
                outHtml = new File(htmlFileName);
            } else {
                // for lambda deployment
                outHtml = new File("/tmp/tmp.html");
            }

            Writer out;
            out = new OutputStreamWriter(
                    new FileOutputStream(
                            outHtml, false), StandardCharsets.UTF_8);
            template.process(dataModel, out);
            ObjectMetadata metaData = new ObjectMetadata();
            metaData.setContentType(contentType);
            PutObjectRequest putObjReq =
                    new PutObjectRequest(s3bucket, objectKey, outHtml)
                            .withCannedAcl(CannedAccessControlList.PublicRead);
            putObjReq.setMetadata(metaData);
            s3.putObject(putObjReq);
            System.out.println("created page: " + objectKey);
            pagesCreated++;
            out.close();
            outHtml.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private List<MatchBean> getAllMatchesByCompetition(String competition) {
        LinkedList<MatchBean> list = new LinkedList<>();
        for (Map.Entry<String, MatchBean> entry : matchesHM.entrySet()) {
            String competitionKey = entry.getValue().getCompetition();
            competitionKey = competitionKey.replace("UEFA", "");
            competitionKey = competitionKey.replace(" ", "");
            if (competitionKey.equalsIgnoreCase(competition)) {
                list.add(entry.getValue());
            }
        }
        System.out.println(competition + " (" + list.size() + ")");
        return list;
    }

    // note: this is the data model for the homepage; will need separate methods for the
    // league/competition and match pages
    // todo: need a separate method for Champions and Europa League because they have
    // todo: Group attribute instead of match week
    private HashMap<String, List<MatchBean>> getDataModelForHomepageByCompetition(String competition, List<MatchBean> matches) {
        Collections.sort(matches);
        HashMap<String, List<MatchBean>> dataModel = new HashMap<>();
        MatchBean tmpMatchBean;
        List<MatchBean> tmpList;
        // this week's feature match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<>();
        tmpList.add(tmpMatchBean);
        dataModel.put("featureMatch", tmpList);
        // sub-section: this week's feature match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<>();
        tmpList.add(tmpMatchBean);
        dataModel.put("subsectionFeatureMatch", tmpList);
        // sub-section: this week's matches (many)
        tmpList = new LinkedList<>(); // todo: needs a method
        tmpList.add(matches.get(1));
        tmpList.add(matches.get(2));
        tmpList.add(matches.get(3));
        dataModel.put("subsectionThisWeek", tmpList);
        // popular / most viewed match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<>();
        tmpList.add(tmpMatchBean);
        dataModel.put("popularMatch", tmpList);
        // ---
        dataModel.put("week0", getMatchesByMatchWeek((LinkedList<MatchBean>) matches, 0));
        dataModel.put("week1", getMatchesByMatchWeek((LinkedList<MatchBean>) matches, 1));
        dataModel.put("week2", getMatchesByMatchWeek((LinkedList<MatchBean>) matches, 2));
        // ---
        dataModel.put("allMatches", matches);
        // ---
        return dataModel;
    }

    // to create pages with specific matches
    // todo: test
    private HashMap<String, List<MatchBean>> getDataModelForCategory(String category, HashMap<String, List<MatchBean>> tmpAllData, int indexStart, int indexEnd) {
        HashMap<String, List<MatchBean>> tmpMap = new HashMap<>();
        List<MatchBean> tmpList = tmpAllData.get(category);
        List<MatchBean> tmpListReturn = new LinkedList<>();
        try {
            for (int i = indexStart; i < indexEnd; i++) {
                tmpListReturn.add(tmpList.get(i));
            }
            tmpMap.put("matches", tmpListReturn);
        } catch (Exception e) {
            // if there is a problem, return data model with all matches in it
            e.printStackTrace();
            tmpMap.put("matches", tmpList);
            return tmpMap;
        }
        return tmpMap;
    }

    private HashMap<String, List<MatchBean>> getDataModelForMatch(MatchBean match) {
        HashMap<String, List<MatchBean>> tmpMap = new HashMap<>();
        List<MatchBean> tmpList = new LinkedList<>();
        tmpList.add(match);
        tmpMap.put("match", tmpList);
        return tmpMap;
    }

    // weekIndex
    // 0: this (latest) week
    // 1: last week
    // 2: two weeks back
    // 3: three weeks back ...
    private String getMatchWeek(LinkedList<MatchBean> matches, int weekIndex) {
        if (matches.size() == 0) return null;
        Collections.sort(matches);
        String currentMW = matches.get(0).getMatchWeek();
        int weekCounter = 0;
        int matchCounter = 0;
        while (weekCounter < weekIndex && matchCounter < matches.size()) {
            if (!matches.get(matchCounter).getMatchWeek().equals(currentMW)) {
                currentMW = matches.get(matchCounter).getMatchWeek();
                weekCounter++;
            }
            matchCounter++;
        }
        if (weekIndex != weekCounter) {
            currentMW = null;
        }
        return currentMW;
    }

    private LinkedList<MatchBean> getMatchesByMatchWeek(LinkedList<MatchBean> allMatches, int weekIndex) {
        if (allMatches.size() == 0) return null;
        Collections.sort(allMatches);
        String matchWeek = getMatchWeek(allMatches, weekIndex);
        return getMatchesByMatchWeek(allMatches, matchWeek);
    }

    private LinkedList<MatchBean> getMatchesByMatchWeek(LinkedList<MatchBean> allMatches, String matchWeek) {
        if (allMatches.size() == 0) return null;
        Collections.sort(allMatches);
        LinkedList<MatchBean> matchesReturn = new LinkedList<>();
        for (MatchBean match : allMatches) {
            if (match.getMatchWeek().equals(matchWeek)) {
                matchesReturn.add(match);
            }
        }
        return matchesReturn;
    }

    private LinkedList<MatchUrlBean> makeMatchUrlBean(String urls) {
        if (urls == null) return null;
        if (urls.equalsIgnoreCase("")) return null;

        //System.out.println(urls);

        LinkedList<MatchUrlBean> list = new LinkedList<>();

        // in case the list was previously stored using the LinkedList.toString()
        // method, we need to check and if needed, remove the [ and ] characters
        if (urls.indexOf("[") == 0 && urls.indexOf("]") == urls.length() - 1) {
            //System.out.println("before:"+urls);
            urls = urls.substring(1, urls.length() - 1);
            //System.out.println("after:"+urls);
        }

        String[] tokens = urls.split(",");
        //String html = "";
        for (int i = 0; i < tokens.length - 1; i = i + 2) {
            // url, label
            String url = tokens[i];
            //String url = "";
            //if(i == 0) {
            //    url = pageName;
            //} else {
            //    url = pageName + "-" + i + "";
            //}
            System.out.println("[SWB][makeMatchUrlBean] url: " + url);
            //url = url + ".html";
            String label = tokens[i + 1];
            System.out.println("[SWB][makeMatchUrlBean] label: " + label);
            list.add(new MatchUrlBean(url, label));
        }
        return list;
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

    public String getTemplatesFolder() {
        return templatesFolder;
    }

    public void setTemplatesFolder(String templatesFolder) {
        this.templatesFolder = templatesFolder;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public void setMatchesPerPage(int matchesPerPage) {
        this.matchesPerPage = matchesPerPage;
    }

    public int getMatchesPerPage() {
        return this.matchesPerPage;
    }
}
