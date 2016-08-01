package com.tinymission.rss;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import org.xml.sax.Attributes;
import android.annotation.SuppressLint;
import android.util.Log;

/**
 * Base class for feed entities that provides automatic property assignment.
 * 
 */
public class FeedEntity implements Serializable
{
	public static final long serialVersionUID = 133701L;
	public static final String LOGTAG = "rss.FeedEntity";
	public static final boolean LOGGING = false;
	
	public FeedEntity(Attributes attributes)
	{
		if (attributes != null)
		{
			for (int i = 0; i < attributes.getLength(); i++)
			{
				String name = attributes.getLocalName(i);
				String value = attributes.getValue(i);
				setProperty(name, value);
			}
		}
	}

	public static HashMap<Class<?>, HashMap<String,Method>> gPropertyMap= new HashMap<Class<?>, HashMap<String,Method>>();
	
	@SuppressLint("DefaultLocale")
	private Method getMethod(String propertyName) {
		synchronized (gPropertyMap) {
			HashMap<String, Method> methodMap = null;
			if (!gPropertyMap.containsKey(getClass())) {
				methodMap = new HashMap<String, Method>();
				gPropertyMap.put(getClass(), methodMap);
			} else {
				methodMap = gPropertyMap.get(getClass());
			}

			if (methodMap.containsKey(propertyName))
				return methodMap.get(propertyName);

			// Try to find it!
			String methodName = "set" + propertyName;

			for (Method m : getClass().getMethods()) {
				if (m.getName().equalsIgnoreCase(methodName)
						&& m.getParameterTypes().length == 1) {
					// a single argument method with the correct name
					methodMap.put(propertyName, m);
					return m;
				}
			}
		}
		return null;
	}
	
	/**
	 * Sets the string value of a property by name.
	 * 
	 * @param name
	 *            the name of the property
	 * @param value
	 *            the string representation of the property value
	 */
	public void setProperty(String name, String value)
	{
		String methodName = LOGGING ? "set" + name : "";
		String logTag = LOGGING ? LOGTAG + ": " + getClass().getName() : "";

		try
		{
			if (LOGGING)
				Log.v(logTag, "Attempting to call " + methodName);

			Method m = getMethod(name);
			if (m != null)
			{
				if (m.getParameterTypes()[0] == String.class)
				{ // it's just a string argument, pass the string
					m.invoke(this, value);
					if (LOGGING)
						Log.v(logTag, "Assigned property " + name + " string value: " + value);
				}
				else if (m.getParameterTypes()[0] == Integer.class)
				{ // it's an int argument, we can handle that
					int intVal = Integer.parseInt(value);
					m.invoke(this, intVal);
					if (LOGGING)
						Log.v(logTag, "Assigned property " + name + " int value: " + Integer.toString(intVal));
				} else {
					if (LOGGING) 
						Log.v(logTag, "Didn't find method with right params");
				}
				return;
			}
			if (LOGGING)
				Log.w(logTag, "Couldn't find property setter " + methodName);
		}
		catch (Exception ex)
		{
			if (LOGGING) {
				Log.w(logTag, "Error setting property: " + name);
				Log.w(logTag, ex.getMessage());
			}
		}
	}

}
