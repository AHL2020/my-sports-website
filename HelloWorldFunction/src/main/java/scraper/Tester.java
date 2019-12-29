package scraper;

import beans.MatchUrlBean;

import java.io.File;
import java.util.LinkedList;
import java.util.Scanner;

public class Tester {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(new File("test-match-page.html"));
        String html = "";
        while(sc.hasNext()) {
            html += sc.nextLine();
        }
        LinkedList<String> list = extractMediaLinks(html);

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
