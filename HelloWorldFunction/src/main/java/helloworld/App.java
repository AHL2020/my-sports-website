package helloworld;

import java.util.HashMap;
import java.util.Map;
import builder.StaticWebsiteBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import scraper.MatchListingsScraper;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<Object, Object> {
    public Object handleRequest(final Object input, final Context context) throws ConditionalCheckFailedException {

        //System.out.println( System.getenv("TEST_VAR") );

        boolean scrapeAll = Boolean.parseBoolean(System.getenv("SCRAPE_ALL"));
        boolean buildAll = Boolean.parseBoolean(System.getenv("BUILD_ALL"));

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");
        try {
            String output = String.format("{ \"message\": \"hello world\", \"location\"}");

            // scrape
            MatchListingsScraper scraper = new MatchListingsScraper();

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
            scraper.setScrapeAll(scrapeAll);

            scraper.process();

            // build
            StaticWebsiteBuilder builder = new StaticWebsiteBuilder();

            builder.addSource("Bundesliga", "bundesliga");
            builder.addSource("PremierLeague", "premierLeague");
            builder.addSource("LaLiga", "laLiga");
            builder.addSource("SerieA", "serieA");
            builder.addSource("Ligue1", "ligue1");
            builder.addSource("ChampionsLeague", "championsLeague");
            builder.addSource("EuropaLeague", "europaLeague");
            builder.addSource("ECQualification", "euro2020");

            builder.setS3bucket("my-sports-website");
            builder.setDatabaseObjectKey("data/database.csv");
            builder.setImagesFolder("images/");
            builder.setPagesFolder("pages/");
            builder.setTemplatesFolder("templates/");
            builder.setOverwrite(buildAll);
            builder.setFreemarkerTemplateDirectoryUrl("https://my-sports-website.s3.amazonaws.com");

            builder.build();

            String websiteUrl = "https://europefootball.net";
            String webTitle = "EuropeFootball.net";
            String webDescr = "Watch full match replays and highlights of Premier League, Champions League, La Liga, Bundesliga, Serie A, Ligue 1 and Europa League";

            builder.generateSEO(websiteUrl, webTitle, webDescr);

            return new GatewayResponse(output, headers, 200);
        } catch (Exception e) {
            return new GatewayResponse("{}", headers, 500);
        }
    }
}
