package com.tinymission.rss;

import java.io.Serializable;
import java.util.Locale;

import org.xml.sax.Attributes;

import android.util.Log;

public class MediaContent extends FeedEntity implements Serializable
{
	public static final long serialVersionUID = 133703L;

	 public enum MediaContentType { IMAGE, VIDEO, AUDIO, APPLICATION, EPUB, UNKNOWN };
	 
	 /* 
	 * private MediaContentType mType = MediaContentType.IMAGE; private int
	 * mResId;
	 * 
	 * public MediaContent(MediaContentType type, int resId) { super(null);
	 * mType = type; mResId = resId; }
	 * 
	 * public MediaContentType getMediaContentType() { return mType; }
	 * 
	 * public int getResId() { return mResId; }
	 */
	public static final int DEFAULT_DATABASE_ID = -1;

	
	private long itemDatabaseId;
	private long databaseId = DEFAULT_DATABASE_ID;

	private boolean downloaded = false;
	
	private String url;
	private String type;

	private String medium;
	private int height;
	private int width;
	private long fileSize;
	private int duration;
	private Boolean isDefault;
	private String expression;
	private int bitrate;
	private int framerate;
	private String lang;
	private String sampligRate;

	// This isn't in the database, just a way to pass it around..
	// Not fully implemented
	private java.io.File downloadedNonVFSFile;
	public void setDownloadedNonVFSFile(java.io.File _downloadedNonVFSFile) {
		downloadedNonVFSFile = _downloadedNonVFSFile;
	}
	public java.io.File getDownloadedNonVFSFile() {
		return downloadedNonVFSFile;
	}
	
	//private Uri localUri;
	//private String filePath;

	public MediaContent(Attributes attributes)
	{
		super(attributes);
	}

	// Local version for local media - posts
	// copy file to encrypted storage
	/*
	public MediaContent(long _itemDatabaseId, File , String _type)
	{
		super(null);
		itemDatabaseId = _itemDatabaseId;
		
		java.io.File deviceFile = getFileFromLocalUri(context, _localUri);
		File encryptedFile = new File(socialReader.getFileSystemDir(), "" + itemDatabaseId);

		MediaContent.copyFile(deviceFile, )
		
		type = _type;
	}
	*/
	
	public MediaContent(long _itemDatabaseId, String _url, String _type)
	{
		super(null);
		itemDatabaseId = _itemDatabaseId;
		if (url == null) {
			url = _url;
			//url = "file:///-1";
		}
		type = _type;
	}

	/*
	public Uri getlocalUri()
	{
		return localUri;
	}
	*/
	
	// Assuming this is an image
	/*
	public java.io.File getFileFromLocalUri(Context context, Uri localUri)
	{
		java.io.File returnFile = null;

		if (localUri != null && localUri.getScheme().equals("content"))
		{
			Cursor cursor = context.getContentResolver().query(localUri, new String[] { android.provider.MediaStore.Images.ImageColumns.DATA }, null, null,
					null);
			cursor.moveToFirst();
			returnFile = new java.io.File(cursor.getString(0));
			cursor.close();
		}
		else if (localUri != null)
		{
			returnFile = new java.io.File(localUri.getPath());
		}
		
		return returnFile;
	}
	*/
	
	/*
	private static void copyFileFromFStoAppFS(java.io.File src, info.guardianproject.iocipher.File dst) throws IOException
	{
		InputStream in = new java.io.FileInputStream(src);
		OutputStream out = new info.guardianproject.iocipher.FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
		{
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}
	*/
	
	public long getItemDatabaseId()
	{
		return itemDatabaseId;
	}

	public void setItemDatabaseId(long itemDatabaseId)
	{
		this.itemDatabaseId = itemDatabaseId;
	}

	public long getDatabaseId()
	{
		return databaseId;
	}

	public void setDatabaseId(long databaseId)
	{
		this.databaseId = databaseId;
	}

	/**
	 * @return the url
	 */
	public String getUrl()
	{
		// This is a bit hacky.. The database has a constraint on the url
		/*if (url == null && localUri != null)
		{
			return localUri.toString();
		}*/

		return url;
	}

	/**
	 * @param url
	 *            the url should specify the direct url to the media object. If
	 *            not included, a <media:player> element must be specified.
	 */
	public void setUrl(String url)
	{
		this.url = url;
	}

	/**
	 * @return the type
	 */
	public String getType()
	{
		if (type == null) {
			setType(android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(android.webkit.MimeTypeMap.getFileExtensionFromUrl(url)));					
		}
		return type;
	}

	/**
	 * @param type
	 *            the type is the standard MIME type of the object. It is an
	 *            optional attribute.
	 */
	public void setType(String type)
	{
		this.type = type;
	}

	/**
	 * @return the medium
	 */
	public String getMedium()
	{
		return medium;
	}

	/**
	 * @param medium
	 *            the medium is the type of object (image | audio | video |
	 *            document | executable). While this attribute can at times seem
	 *            redundant if type is supplied, it is included because it
	 *            simplifies decision making on the reader side, as well as
	 *            flushes out any ambiguities between MIME type and object type.
	 *            It is an optional attribute.
	 */
	public void setMedium(String medium)
	{
		this.medium = medium;
	}

	/**
	 * @return the height
	 */
	public int getHeight()
	{
		return height;
	}

	/**
	 * @param height
	 *            the height is the height of the media object. It is an
	 *            optional attribute.
	 */
	public void setHeight(int height)
	{
		this.height = height;
	}

	/**
	 * @return the width
	 */
	public int getWidth()
	{
		return width;
	}

	/**
	 * @param width
	 *            the width is the width of the media object. It is an optional
	 *            attribute.
	 */
	public void setWidth(int width)
	{
		this.width = width;
	}

	/**
	 * @return the fileSize
	 */
	public long getFileSize()
	{
		return fileSize;
	}

	/**
	 * @param fileSize
	 *            the fileSize is the number of bytes of the media object. It is
	 *            an optional attribute.
	 */
	public void setFileSize(long fileSize)
	{
		this.fileSize = fileSize;
	}

	/**
	 * @return the duration
	 */
	public int getDuration()
	{
		return duration;
	}

	/**
	 * @param duration
	 *            the duration is the number of seconds the media object plays.
	 *            It is an optional attribute.
	 */
	public void setDuration(int duration)
	{
		this.duration = duration;
	}

	/**
	 * @return whether this is the default object for this <media:group>
	 */
	public Boolean getIsDefault()
	{
		return isDefault;
	}

	/**
	 * @param isDefault
	 *            determines if this is the default object that should be used
	 *            for the <media:group>. There should only be one default object
	 *            per <media:group>. It is an optional attribute.
	 */
	public void setIsDefault(Boolean isDefault)
	{
		this.isDefault = isDefault;
	}

	/**
	 * @return the expression
	 */
	public String getExpression()
	{
		return expression;
	}

	/**
	 * @param expression
	 *            the expression determines if the object is a sample or the
	 *            full version of the object, or even if it is a continuous
	 *            stream (sample | full | nonstop). Default value is 'full'. It
	 *            is an optional attribute.
	 */
	public void setExpression(String expression)
	{
		this.expression = expression;
	}

	/**
	 * @return the bitrate
	 */
	public int getBitrate()
	{
		return bitrate;
	}

	/**
	 * @param bitrate
	 *            the bitrate is the kilobits per second rate of media. It is an
	 *            optional attribute.
	 */
	public void setBitrate(int bitrate)
	{
		this.bitrate = bitrate;
	}

	/**
	 * @return the framerate
	 */
	public int getFramerate()
	{
		return framerate;
	}

	/**
	 * @param framerate
	 *            the framerate is the number of frames per second for the media
	 *            object. It is an optional attribute.
	 */
	public void setFramerate(int framerate)
	{
		this.framerate = framerate;
	}

	/**
	 * @return the lang
	 */
	public String getLang()
	{
		return lang;
	}

	/**
	 * @param lang
	 *            the lang is the primary language encapsulated in the media
	 *            object. Language codes possible are detailed in RFC 3066. This
	 *            attribute is used similar to the xml:lang attribute detailed
	 *            in the XML 1.0 Specification (Third Edition). It is an
	 *            optional attribute.
	 */
	public void setLang(String lang)
	{
		this.lang = lang;
	}

	/**
	 * @return the sampligRate
	 */
	public String getSampligRate()
	{
		return sampligRate;
	}

	/**
	 * @param sampligRate
	 *            the sampligRate is the number of samples per second taken to
	 *            create the media object. It is expressed in thousands of
	 *            samples per second (kHz). It is an optional attribute.
	 */
	public void setSampligRate(String sampligRate)
	{
		this.sampligRate = sampligRate;
	}
	
	public void setDownloaded(boolean _downloaded) {
		this.downloaded = _downloaded;
	}
	
	public boolean getDownloaded()
	{
		return downloaded;
	}

	public MediaContentType getMediaContentType() 
	{
		if (getType() != null && getType().equals("application/vnd.android.package-archive")) 
		{
			return MediaContentType.APPLICATION;
		}
		else if (getType() != null && getType().equals("application/epub+zip")) 
		{
			return MediaContentType.EPUB;
		}
		else if ((getType() != null && getType().startsWith("image")) || 
				"image".equals(getMedium()) ||
				matchesFileExtension(".jpg") || 
				matchesFileExtension(".jpeg"))
		{
			return MediaContentType.IMAGE;
		}
		else if ((getType() != null && getType().startsWith("audio"))
				|| "audio".equals(getMedium()))
		{
			return MediaContentType.AUDIO;
		}
		else if ((getType() != null && getType().startsWith("video"))
				|| "video".equals(getMedium()))
		{
			return MediaContentType.VIDEO;
		}
		return MediaContentType.UNKNOWN;
	}
		
	private boolean matchesFileExtension(String extension)
	{
		if (extension == null || getUrl() == null)
			return false;
		return getUrl().toLowerCase(Locale.getDefault()).endsWith(extension.toLowerCase(Locale.getDefault()));
	}
	
}
