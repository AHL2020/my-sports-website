package shared;

public class ContentTypeManager {
    public static String getContentTypeByExtension(String fileExtension) {
        switch(fileExtension) {
            case "jpg" : return "image/jpeg";
            case "jpeg" : return "image/jpeg";
            case "png" : return "image/png";
            case "csv" : return "text/plain";
            case "html" : return "text/html";
            default : return "text/plain";
        }
    }
    public static String getFileExtension(String filename) {
        String extension = "";
        int p = filename.lastIndexOf(".");
        if(p == -1) {
            return "";
        }
        extension = filename.substring(p+1);
        return extension;
    }
    public static String getContentType(String filename) {
        String extension = getFileExtension(filename);
        return getContentTypeByExtension(extension);
    }
    public static String getVideoPlayer(String videoLink) {
        String videoLinkLowerCase = videoLink.toLowerCase();
        String[] players = {"voo", "brid"};
        for(String player: players) {
            if(videoLinkLowerCase.indexOf(player) != -1) return player;
        }
        return "default";
    }
}
