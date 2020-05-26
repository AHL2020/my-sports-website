package builder;

import beans.MatchUrlBean;
import com.amazonaws.services.s3.model.CannedAccessControlList;
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

    private Regions REGION = Regions.US_EAST_1;
    private HashMap<String, MatchBean> matchesHM = null;
    private String s3bucket = "";
    private String databaseObjectKey = "";
    private String imagesFolder = "";
    private String pagesFolder = "";
    private String templatesFolder = "";
    private boolean overwrite = false;
    private String freemarkerTemplateDirectoryUrl = "";
    private AmazonS3 s3 = null;
    HashMap<String, String> sources = null;
    private int pagesCreated = 0;

    public static int CLOUD = 0;
    public static int LOCAL = 1;
    private int deploymentType = StaticWebsiteBuilder.LOCAL;

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
        builder.setFreemarkerTemplateDirectoryUrl("https://my-sports-website.s3.amazonaws.com");

        builder.build();

        String websiteUrl = "https://europefootball.net";
        String webTitle = "EuropeFootball.net";
        String webDescr = "Watch full match replays and highlights of Premier League, Champions League, La Liga, Bundesliga, Serie A, Ligue 1 and Europa League";

        builder.generateSEO(websiteUrl, webTitle, webDescr);
    }

    public StaticWebsiteBuilder(int deploymentType) {
        if(deploymentType < 0 || deploymentType > 1) {
            this.deploymentType = StaticWebsiteBuilder.LOCAL;
        } else {
            this.deploymentType = deploymentType;
        }
        System.out.println("[StaticWebsiteBuilder] running in [" + getDeploymentTypeName() + "] mode.");
        s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();
        sources = new HashMap<String, String>();
    }

    public int getDeploymentType() {
        return this.deploymentType;
    }

    public String getDeploymentTypeName() {
        if(this.deploymentType == StaticWebsiteBuilder.CLOUD) return "CLOUD"; else return "LOCAL";
    }

    public void addSource(String nameKey, String templateKey) {
        sources.put(nameKey, templateKey);
    }

    public boolean generateSEO(String websiteUrl, String webTitle, String webDescr) {
        System.out.println("Generating SEO ...");
        if(matchesHM == null) return false;
        SEOGenerator seo = new SEOGenerator(websiteUrl, webTitle, webDescr, 15);
        //--
        sources.forEach((key, value) -> {
            seo.addPage(key+".html", "");
        });
        seo.addPage("Contact.html", "");
        seo.addPage("About.html", "");
        seo.addPage("index.html", "");
        //--
        boolean result = seo.process(DataUtils.convertHMtoLL(matchesHM));
        if(result == false) {
            return false;
        } else {
            String sitemap = seo.getSitemap();
            String rssfeed = seo.getRssfeed();
            String robots = seo.getRobots();
            DataUtils.saveContentToS3(s3bucket, "sitemap.xml", sitemap, "text/xml");
            DataUtils.saveContentToS3(s3bucket, "rssfeed.xml", rssfeed, "text/xml");
            DataUtils.saveContentToS3(s3bucket, "robots.txt", robots, "text/plain");
        }
        return true;
    }

    public void build() {

        try {
            // Load data from S3
            System.out.println("Load data from S3 ... ");
            matchesHM = DataUtils.loadMatchBeanObjects(s3bucket, databaseObjectKey);
            System.out.println("Loaded match data from S3: " + matchesHM.size());

            // data structure to store all matches data
            HashMap<String,List<MatchBean>> tmpAllData = new HashMap<String,List<MatchBean>>();

            // list of all matches -> for randoms etc
            LinkedList<MatchBean> allMatches = new LinkedList<MatchBean>();

            // the data model that will be processed by Free Marker into the template
            Map<String, HashMap<String,List<MatchBean>>> dataModel = new HashMap<String, HashMap<String, List<MatchBean>>>();

            System.out.println("loading data ...");

            // process each league
            sources.forEach((key, value) -> {
                List<MatchBean> tmpList = getAllMatchesByCompetition(key);
                LinkedList<MatchBean> tmpLL = DataUtils.convertPSLtoLL(tmpList);
                Collections.sort(tmpLL);
                // set correct image and page location URLs
                for(MatchBean match : tmpLL) {
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

            HashMap<String, List<MatchBean>> tmpHashMap = new HashMap<String, List<MatchBean>>();
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
            for(Map.Entry<String, String> league : sources.entrySet()) {
                dataModel.put("matches", getDataModelForCategory(league.getKey(), tmpAllData));
                // set path
                dataModel.put("path2", DataUtils.makePath(""));
                htmlFileName = league.getKey() + ".html";
                //set competition key
                dataModel.put("competitionKey", DataUtils.makeCompetitionKey(league.getKey()));

                createPage(dataModel, htmlFileName, relativePath, templateName, true);

            }

            // create match pages

            System.out.println("create match pages ...");
            // set path
            dataModel.put("path2", DataUtils.makePath("../../"));
            templateName = templatesFolder + "match.ftlh";
            for(String competition: tmpAllData.keySet()) {
                LinkedList<MatchBean> matches = (LinkedList<MatchBean>) tmpAllData.get(competition);
                Collections.sort(matches);
                for(int i = 0; i < matches.size(); i++) {
                    MatchBean match = matches.get(i);
                    if(overwrite == true || match.getPageCreatedDate() == null) {
                        if (match.isValid()) {
                            if (match.getPageCreatedDate() == null) {
                                match.setPageCreatedDate(Instant.now());
                            } else {
                                match.setPageLastUpdatedDate(Instant.now());
                            }
                            match.setMatchUrlsAndTags(makeMatchUrlBean(match.getMatchUrls()));
                            match.setPageCreatedDate(Instant.now());
                            dataModel.put("match", getDataModelForMatch(match));
                            htmlFileName = match.getMatchKey() + ".html";
                            relativePath = pagesFolder + competition + "/";
                            //set competition key
                            //System.out.println(competition);
                            dataModel.put("competitionKey", DataUtils.makeCompetitionKey(competition));
                            createPage(dataModel, htmlFileName, relativePath, templateName, overwrite);
                            //System.out.print(".");
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

    public String getFreemarkerTemplateDirectoryUrl() {
        return freemarkerTemplateDirectoryUrl;
    }

    public void setFreemarkerTemplateDirectoryUrl(String freemarkerTemplateDirectoryUrl) {
        this.freemarkerTemplateDirectoryUrl = freemarkerTemplateDirectoryUrl;
    }

    private boolean createPage(Map<String, HashMap<String,List<MatchBean>>> dataModel, String htmlFileName, String relativePath, String templateName, boolean overwrite) {
        try {
            //System.out.println("createPage: htmlFileName:[" + htmlFileName + "], overwrite: [" + overwrite + "]");

            // save the html file to S3
            String contentType = "text/html";
            String objectKey = relativePath + htmlFileName;
            boolean objectExists = s3.doesObjectExist(s3bucket, objectKey);
            //System.out.println("objectExists: [" + objectExists + "], overwrite: [" + overwrite + "]");

            if(overwrite==true || objectExists==false) {

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

                File outHtml = null;
                if(this.deploymentType == StaticWebsiteBuilder.LOCAL) {
                    // for local deployment
                    outHtml = new File(htmlFileName);
                } else {
                    // for lambda deployment
                    outHtml = new File("/tmp/tmp.html");
                }

                Writer out = null;
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
            }


        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private List<MatchBean> getAllMatchesByCompetition(String competition) {
        LinkedList<MatchBean> list = new LinkedList<MatchBean>();
        for (Map.Entry<String, MatchBean> entry : matchesHM.entrySet()) {
            String competitionKey = entry.getValue().getCompetition();
            competitionKey = competitionKey.replace("UEFA", "");
            competitionKey = competitionKey.replace(" ", "");
            if(competitionKey.equalsIgnoreCase(competition)) {
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
        HashMap<String, List<MatchBean>> dataModel = new HashMap<String, List<MatchBean>>();
        MatchBean tmpMatchBean = new MatchBean();
        List<MatchBean> tmpList = new LinkedList<MatchBean>();
        // this week's feature match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<MatchBean>();
        tmpList.add(tmpMatchBean);
        dataModel.put("featureMatch", tmpList);
        // sub-section: this week's feature match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<MatchBean>();
        tmpList.add(tmpMatchBean);
        dataModel.put("subsectionFeatureMatch", tmpList);
        // sub-section: this week's matches (many)
        tmpList = new LinkedList<MatchBean>(); // todo: needs a method
        tmpList.add(matches.get(1));
        tmpList.add(matches.get(2));
        tmpList.add(matches.get(3));
        dataModel.put("subsectionThisWeek", tmpList);
        // popular / most viewed match (1)
        tmpMatchBean = matches.get(0); // todo: needs a method
        tmpList = new LinkedList<MatchBean>();
        tmpList.add(tmpMatchBean);
        dataModel.put("popularMatch", tmpList);
        // ---
        dataModel.put("week0", getMatchesByMatchWeek((LinkedList)matches, 0));
        dataModel.put("week1", getMatchesByMatchWeek((LinkedList)matches, 1));
        dataModel.put("week2", getMatchesByMatchWeek((LinkedList)matches, 2));
        // ---
        dataModel.put("allMatches", matches);
        // ---
        return dataModel;
    }

    private HashMap<String,List<MatchBean>> getDataModelForCategory(String category, HashMap<String,List<MatchBean>> tmpAllData) {
        HashMap<String,List<MatchBean>> tmpMap = new HashMap<String,List<MatchBean>>();
        List<MatchBean> tmpList = tmpAllData.get(category);
        tmpMap.put("matches", tmpList);
        return tmpMap;
    }

    private HashMap<String,List<MatchBean>> getDataModelForMatch(MatchBean match) {
        HashMap<String,List<MatchBean>> tmpMap = new HashMap<String,List<MatchBean>>();
        List<MatchBean> tmpList = new LinkedList<MatchBean>();
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
        if(matches.size()==0) return null;
        Collections.sort(matches);
        String currentMW = matches.get(0).getMatchWeek();
        int weekCounter = 0;
        int matchCounter = 0;
        while(weekCounter < weekIndex && matchCounter < matches.size()) {
            if(!matches.get(matchCounter).getMatchWeek().equals(currentMW)) {
                currentMW = matches.get(matchCounter).getMatchWeek();
                weekCounter++;
            }
            matchCounter++;
        }
        if(weekIndex != weekCounter) {
            currentMW = null;
        }
        return currentMW;
    }

    private LinkedList<MatchBean> getMatchesByMatchWeek(LinkedList<MatchBean> allMatches, int weekIndex) {
        if(allMatches.size()==0) return null;
        Collections.sort(allMatches);
        String matchWeek = getMatchWeek(allMatches, weekIndex);
        return getMatchesByMatchWeek(allMatches, matchWeek);
    }

    private LinkedList<MatchBean> getMatchesByMatchWeek(LinkedList<MatchBean> allMatches, String matchWeek) {
        if(allMatches.size()==0) return null;
        Collections.sort(allMatches);
        LinkedList<MatchBean> matchesReturn = new LinkedList<MatchBean>();
        for(MatchBean match: allMatches) {
            if(match.getMatchWeek().equals(matchWeek)) {
                matchesReturn.add(match);
            }
        }
        return matchesReturn;
    }

    private LinkedList<MatchUrlBean> makeMatchUrlBean(String urls) {
        if(urls == null) return null;
        if(urls.equalsIgnoreCase("")) return null;

        //System.out.println(urls);

        LinkedList<MatchUrlBean> list = new LinkedList<MatchUrlBean>();

        // in case the list was previously stored using the LinkedList.toString()
        // method, we need to check and if needed, remove the [ and ] characters
        if(urls.indexOf("[") == 0 && urls.indexOf("]") == urls.length()-1) {
            //System.out.println("before:"+urls);
            urls = urls.substring(1, urls.length() - 1);
            //System.out.println("after:"+urls);
        } else {
            //System.out.println("ok:"+urls);
        }

        String[] tokens = urls.split(",");
        String html = "";
        for(int i = 0; i < tokens.length-1; i=i+2) {
            list.add(new MatchUrlBean(tokens[i], tokens[i+1]));
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
}
