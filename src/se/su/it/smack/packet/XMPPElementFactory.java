/*
 * Created on Aug 15, 2005
 *
 */
package se.su.it.smack.packet;

import org.xmlpull.v1.XmlPullParser;

public class XMPPElementFactory {

	public XMPPElementFactory() {
		super();
	}

	public static XMPPElement create(String name) throws Exception
	{
		// remove the '-' from the name
		char[] nameArray = name.toCharArray();
		for(int i = 0; i < name.length(); i++)
			if(i == 0)
				nameArray[0] = Character.toUpperCase(nameArray[0]);
			else if (nameArray[i] == '-' && i != name.length() - 1)
				nameArray[i + 1] = Character.toUpperCase(nameArray[i + 1]);
		name = new String(nameArray).replace("-", "");
//		String className = "se.su.it.smack.pubsub.elements."+name.substring(0,1).toUpperCase()+name.substring(1)+"Element";
		String className = "se.su.it.smack.pubsub.elements."+name+"Element";
		Class cls = Class.forName(className);
		XMPPElement elt = (XMPPElement)cls.newInstance();
		return elt;
	}

	public static XMPPElement create(XmlPullParser pp) throws Exception
	{
		pp.require(XmlPullParser.START_TAG,null,null);
		XMPPElement elt = create(pp.getName());
		elt.parse(pp);
		return elt;
	}

}
