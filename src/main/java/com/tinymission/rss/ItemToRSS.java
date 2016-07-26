package com.tinymission.rss;

import android.util.Log;

import org.apache.commons.lang.StringEscapeUtils;

import java.util.ArrayList;
import java.util.Date;

import info.guardianproject.securereader.SocialReader;

public class ItemToRSS {

    public static final boolean LOGGING = true;
    public static final String LOGTAG = "ItemToRSS";

    public static String toRSS(Item item, Feed feed) {

        StringBuffer outputString = new StringBuffer();

        outputString.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n");
        outputString.append("<rss version=\"2.0\">\n\n");
            outputString.append("<channel>\n");
                outputString.append("<title>"+feed.getTitle()+"</title>\n");
                outputString.append("<link>"+feed.getLink()+"</link>\n");
                outputString.append("<description>"+feed.getDescription()+"</description>\n");
                outputString.append("<item>\n");
                    outputString.append("<title>"+item.getTitle()+"</title>\n");
                    outputString.append("<link>"+item.getLink()+"</link>\n");
                    outputString.append("<description>"+item.getDescription()+"</description>\n");
                    outputString.append("<guid>"+item.getGuid()+"</guid>\n");
                    outputString.append("<author>"+item.getAuthor()+"</author>\n");
                    outputString.append("<pubDate>"+item.getPubDate()+"</publDate>\n");
                    outputString.append("<content:encoded><![CDATA[");
                    outputString.append(StringEscapeUtils.escapeXml(item.getContentEncoded()));
                    outputString.append("]]></content:encoded>\n");
                    ArrayList <String> categories = item.getCategories();
                    for (int c = 0; c < categories.size(); c++) {
                        outputString.append("<category>");
                            outputString.append(categories.get(c));
                        outputString.append("</category>\n");
                    }
                    outputString.append("<comments>"+ item.getCommentsUrl() +"</comments>\n");
                    outputString.append("<author>"+ item.getAuthor() + "</author>\n");


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
