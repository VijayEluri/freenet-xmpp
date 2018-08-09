package se.su.it.smack.pubsub.elements;

public class AffiliationsElement extends PubSubElement {

    public AffiliationsElement() {
	super();
    }
    
    public AffiliationsElement(String node) {
	super(node);
    }
    
    public String getName() {
	return "affiliations";
    }
    
    public String getBeginXMLAttributes() {
	return "node='" + getNode() + "'";
    }
    
}
