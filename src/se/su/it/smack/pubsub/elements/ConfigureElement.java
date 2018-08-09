package se.su.it.smack.pubsub.elements;

import java.util.Map;
import java.util.HashMap;

/**
 * Used with <code>CreateElement</code> to configure a created node.
 * 
 * @author Mikko Pohja
 * 
 */
public class ConfigureElement extends PubSubElement {

    private Map<String, String> fields;

    public ConfigureElement() {
	super();
    }
    
    public ConfigureElement(String node){
	super(node);
    }
    
    public String getName() {
	return "configure";
    }

    /**
     * Adds a field element inside the configure element.
     * 
     * @param name
     *            Name of the field.
     * @param value
     *            Value of the field.
     */
    public void addField(String name, String value) {
	if (fields == null)
	    fields = new HashMap<String, String>();

	fields.put(name, value);
    }

    public String toXML() {
	if (fields == null || fields.isEmpty())
	    if(getNode() == null)
	    return "  <configure/>\n";
	    else
		return "  <configure node='" + getNode() + "'/>\n";
	else {
	    StringBuilder builder = new StringBuilder();
	    if (getNode() == null)
		builder.append("<configure>\n");
	    else
		builder.append("<configure node='").append(getNode()).append("'>\n");
	    builder.append("<x xmlns='jabber:x:data' type='submit'>\n");
	    builder.append("    <field var='FORM_TYPE' type='hidden'>");
	    builder.append("<value>http://jabber.org/protocol/pubsub#node_config</value>");
	    builder.append("</field>\n");
	    for (Map.Entry<String, String> entry : fields.entrySet()) {
		builder.append("      <field var='").append(entry.getKey()).append("'><value>")
			.append(entry.getValue()).append("</value></field>\n");
	    }
	    builder.append("    </x>\n  </configure>\n");
	    return builder.toString();
	}
    }

}
