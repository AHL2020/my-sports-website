package builder;

import freemarker.cache.URLTemplateLoader;

import java.net.MalformedURLException;
import java.net.URL;

public class S3TemplateLoader extends URLTemplateLoader {

    private URL rootUrl;

    public S3TemplateLoader(URL rootUrl) {
        super();
        this.rootUrl = rootUrl;
    }

    @Override
    protected URL getURL(String templateName) {
        try {
            URL templateUrl = new URL(rootUrl, templateName);
            return templateUrl;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }
}