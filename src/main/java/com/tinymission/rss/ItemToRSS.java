package com.tinymission.rss;

import android.util.Log;

import org.apache.commons.lang.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Date;

import info.guardianproject.securereader.SocialReader;

public class ItemToRSS {

    public static final boolean LOGGING = false;
    public static final String LOGTAG = "ItemToRSS";

    public static String toRSS(Item item, Feed feed) {

        StringBuffer outputString = new StringBuffer();

        outputString.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        outputString.append("<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss\" xmlns:content=\"http://purl.org/rss/1.0/modules/content/\">\n\n");
            outputString.append("<channel>\n");
                outputString.append("<title>"+StringEscapeUtils.escapeXml(feed.getTitle())+"</title>\n");
                outputString.append("<link>"+StringEscapeUtils.escapeXml(feed.getLink())+"</link>\n");
                outputString.append("<description>"+StringEscapeUtils.escapeXml(feed.getDescription())+"</description>\n");
                outputString.append("<item>\n");
                    outputString.append("<title>"+StringEscapeUtils.escapeXml(item.getTitle())+"</title>\n");
                    outputString.append("<link>"+StringEscapeUtils.escapeXml(item.getLink())+"</link>\n");
                    outputString.append("<description>"+StringEscapeUtils.escapeXml(item.getDescription())+"</description>\n");
                    outputString.append("<guid>"+StringEscapeUtils.escapeXml(item.getGuid())+"</guid>\n");
                    if (item.getAuthor() != null) {
                        outputString.append("<author>" + StringEscapeUtils.escapeXml(item.getAuthor()) + "</author>\n");
                    }
                    //outputString.append("<pubDate>"+item.getPubDate()+"</publDate>\n");
                    if (item.getContentEncoded() != null) {
                        outputString.append("<content:encoded><![CDATA[");
                        outputString.append(StringEscapeUtils.escapeXml(item.getContentEncoded()));
                        outputString.append("]]></content:encoded>\n");
                    }
                    ArrayList <String> categories = item.getCategories();
                    for (int c = 0; c < categories.size(); c++) {
                        outputString.append("<category>");
                            outputString.append(StringEscapeUtils.escapeXml(categories.get(c)));
                        outputString.append("</category>\n");
                    }
                    if (item.getCommentsUrl() != null) {
                        outputString.append("<comments>" + StringEscapeUtils.escapeXml(item.getCommentsUrl()) + "</comments>\n");
                    }

                    for (int m = 0; m < item.getMediaContent().size(); m++) {
                        outputString.append("<media:content url=\"" + item.getMediaContent(m).getUrl() + "\"" +
                                " fileSize=\"" + item.getMediaContent(m).getFileSize() + "\"" +
                                " type=\"" + item.getMediaContent(m).getType() + "\"></media:content>\n");
                    }

                outputString.append("</item>\n");
            outputString.append("</channel>\n");
        outputString.append("</rss>\n");

        if (LOGGING)
            Log.v(LOGTAG, outputString.toString());

        return outputString.toString();
    }
}
