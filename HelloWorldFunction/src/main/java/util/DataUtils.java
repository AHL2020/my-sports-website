package util;

import beans.MatchBean;
import beans.MatchUrlBean;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.opencsv.CSVReader;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.text.SimpleDateFormat;
import java.nio.CharBuffer;

public class DataUtils {

    private static Regions REGION = Regions.US_EAST_1;
    private static final Charset utf8charset = Charset.forName("UTF-8");
    private static final Charset iso88591charset = Charset.forName("ISO-8859-1");

    public static void main(String[] args) {
        Date testDate1 = DataUtils.convertStringToDate("EEEE dd MMMM yyyy", "Saturday 19 October 2019");
        Date testDate2 = DataUtils.convertStringToDate("EEEE, dd MMMM yyyy", "Saturday, 05 October 2019");
        System.out.println(testDate1);
        System.out.println(testDate2);
    }

    public static String toISO8859_1(String text) {
        try {

            ByteBuffer inputBuffer = ByteBuffer.wrap(text.getBytes(utf8charset));
            // decode UTF-8
            CharBuffer data = utf8charset.decode(inputBuffer);
            // encode ISO-8559-1
            ByteBuffer outputBuffer = iso88591charset.encode(data);
            byte[] outputData = outputBuffer.array();

            return new String(outputData, iso88591charset);

        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static LinkedList<MatchBean> convertHMtoLL(HashMap<String, MatchBean> matches) {
        LinkedList<MatchBean> matchesLL = new LinkedList<MatchBean>();
        for(Map.Entry<String, MatchBean> match : matches.entrySet()) {
            matchesLL.add(match.getValue());
        }
        Collections.sort(matchesLL);
        return matchesLL;
    }

    public static LinkedList<MatchBean> convertPSLtoLL(List<MatchBean> psl) {
        LinkedList<MatchBean> list = new LinkedList<MatchBean>();
        for(MatchBean m: psl) {
            list.add(m);
        }
        return list;
    }
    public static Date convertStringToDate(String dateString) {
        String pattern = "EEEE, dd MMMM yyyy";
        return convertStringToDate(pattern, dateString);
    }
    public static Date convertStringToDate(String pattern, String dateString) {
        SimpleDateFormat simpleDateFormat = null;
        Date date = null;
        try {
            simpleDateFormat = new SimpleDateFormat(pattern);
            date = simpleDateFormat.parse(dateString);
        } catch (ParseException e) {
            //e.printStackTrace();
            return null;
        }
        return date;
    }

    public static HashMap<String, MatchBean> loadMatchBeanObjects(String bucketName, String databaseKey) {
        //System.out.println("start: loadMatchBeanObjects");
        AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();
        //System.out.println("s3 object build()");
        HashMap<String, MatchBean> matches = new HashMap<String, MatchBean>();
        //System.out.println("creating BR for bucketName: [" + bucketName + "] and databaseKey: [" + databaseKey + "]");

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(s3.getObject(
                new GetObjectRequest(bucketName, databaseKey)).getObjectContent()));
            //System.out.println("BufferedReader created");
            MatchBean tmpMatchBean = new MatchBean();
            CSVReader reader = null;

           // System.out.println("Trying to read s3 object ...");
            reader = new CSVReader(bufferedReader);
            String[] line;
            while ((line = reader.readNext()) != null) {

                // dates
                Instant scraped = null;
                Instant created = null;
                Instant updated = null;

                //System.out.println("[" + line[13] + "]");
                //System.out.println("[" + line[14] + "]");
                //System.out.println("[" + line[15] + "]");
                //System.exit(0);

                if(line.length > 13 && !line[13].equalsIgnoreCase("null")) scraped = Instant.parse(line[13]);
                if(line.length > 14 && !line[14].equalsIgnoreCase("null")) created = Instant.parse(line[14]);
                if(line.length > 15 && !line[15].equalsIgnoreCase("null")) updated = Instant.parse(line[15]);

                tmpMatchBean = new MatchBean(
                        line[0]
                        , line[1]
                        , line[2]
                        , line[3]
                        , line[4]
                        , line[5]
                        , line[6]
                        , line[7]
                        , line[8]
                        , line[9]
                        , line[10]
                        , new LinkedList<MatchUrlBean>()
                        , line[12]
                        , scraped
                        , created
                        , updated);
                matches.put(tmpMatchBean.getMatchKey(), tmpMatchBean);

                //System.out.println("Loaded from database: " + tmpMatchBean.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error loading from database: " + e.toString());
        }
        System.out.println("Successfully loaded " + matches.size() + " matches from database.");
        return matches;
    }

    public static boolean saveMatchBeanObjects(HashMap<String, MatchBean> matches, String bucketName, String databaseKey) {
        try {
            AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();
            StringBuffer data = new StringBuffer();
            MatchBean mb = new MatchBean();
            for (Map.Entry<String, MatchBean> entry : matches.entrySet()) {
                mb = entry.getValue();
                data.append(mb.toCsvString());
                data.append("\n");
            }
            s3.putObject(bucketName, databaseKey, data.toString());
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean saveContentToS3(String bucketName, String objectKey, String content, String contentType) {
        try {
            //AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();

            AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(REGION).build();
            byte[] fileContentBytes = content.getBytes(StandardCharsets.UTF_8);
            InputStream in = new ByteArrayInputStream(fileContentBytes);
            ObjectMetadata metaData = new ObjectMetadata();
            metaData.setContentType(contentType);
            metaData.setContentLength(fileContentBytes.length);
            PutObjectRequest putObjReq =
                    new PutObjectRequest(bucketName, objectKey, in, metaData)
                            .withCannedAcl(CannedAccessControlList.PublicRead);
            s3.putObject(putObjReq);

            //s3.putObject(bucketName, objectKey, content);
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static String strip(String s) {
        if(s.length() < 2) return s;
        return s.substring(1, s.length()-1);
    }

    public static HashMap<String, List<MatchBean>> makePath(String path) {
        HashMap<String, List<MatchBean>> map = new HashMap<String, List<MatchBean>>();
        List<MatchBean> list = new LinkedList<MatchBean>();
        MatchBean match = new MatchBean();
        match.setMatchKey(path);
        list.add(match);
        map.put("path", list);
        return map;
    }

    /**
     * the idea is to use this method as a 'hack' to set ad-hoc key/value pairs
     * for the Freemarker template. E.g. if I want to tell the template to set
     * 'Page 2 of 7' I can use this method for it.
     * @param key: String
     * @param value: String
     * @return dataModel for Freemarker
     */
    // todo: test
    public static HashMap<String, List<MatchBean>> setTemplateAttribute(String key, String value) {
        HashMap<String, List<MatchBean>> map = new HashMap<String, List<MatchBean>>();
        List<MatchBean> list = new LinkedList<MatchBean>();
        MatchBean match = new MatchBean();
        match.setMatchKey(value);
        list.add(match);
        map.put(key, list);
        return map;
    }

    /**
     * the idea is to use this method as a 'hack' to set ad-hoc key/value pairs
     * for the Freemarker template. E.g. if I want to tell the template to set
     * 'Page 2 of 7' I can use this method for it.
     * @param key: String
     * @param valueList: List<String>
     * @return dataModel for Freemarker
     */
    // todo: test
    public static HashMap<String, List<MatchBean>> setTemplateAttributeList(String key, List<String> valueList) {
        HashMap<String, List<MatchBean>> map = new HashMap<String, List<MatchBean>>();
        List<MatchBean> list = new LinkedList<MatchBean>();
        for(String value: valueList) {
            MatchBean match = new MatchBean();
            match.setMatchKey(value);
            list.add(match);
        }
        map.put(key, list);
        return map;
    }

    public static HashMap<String, List<MatchBean>> makeCompetitionKey(String competitionKey) {
        HashMap<String, List<MatchBean>> map = new HashMap<String, List<MatchBean>>();
        List<MatchBean> list = new LinkedList<MatchBean>();
        MatchBean match = new MatchBean();
        match.setCompetition(competitionKey);
        list.add(match);
        map.put("competitionKey", list);
        return map;
    }

}
