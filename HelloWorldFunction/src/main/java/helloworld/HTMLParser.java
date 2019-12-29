package helloworld;

import org.jsoup.Jsoup;
import org.jsoup.helper.Validate;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

public class HTMLParser {
    public static String parseTestDummy(String input) {
        return "parseTestDummy: " + input;
    }
    public static String parseURL(String url) throws Exception {
        StringBuffer output = new StringBuffer("");

        output.append("Fetching %s..." + url + "\n");

        Document doc = Jsoup.connect(url).get();
        Elements links = doc.select("a[href]");
        Elements media = doc.select("[src]");
        Elements imports = doc.select("link[href]");

        output.append("\nMedia: (%d)" + media.size() + "\n");
        for (Element src : media) {
            if (src.tagName().equals("img")) {
                output.append(" * ");
                output.append(
                        src.tagName() + " " +
                                src.attr("abs:src") + " " +
                                src.attr("width") + " " +
                                src.attr("height") + " " +
                                src.attr("alt"));
            }
            else
                output.append(" * " + src.tagName() + " " + src.attr("abs:src"));
        }

        output.append("\nImports: " + imports.size());
        for (Element link : imports) {
            output.append(" * " + link.tagName() + " " + link.attr("abs:href") + " " + link.attr("rel"));
        }

        output.append("\nLinks: " + links.size());
        for (Element link : links) {
            output.append(" * a: " + link.attr("abs:href") + " " + link.text());
        }

        return output.toString();
    }

    private static void print(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

}