package se.su.it.smack.pubsub.elements;

public class AffiliationElement extends PubSubElement {

    public enum Type {
	owner, publisher, member, none, outcast
    };

    private String jid;
    private Type affiliation;

    public AffiliationElement() {

    }

    public AffiliationElement(String jid, Type affiliation) {
	this.jid = jid;
	this.affiliation = affiliation;
    }

    public String getName() {
	return "affiliation";
    }

    @Override
    public String getBeginXMLAttributes() {
	return "jid='" + getJid() + "' affiliation='" + getAffiliation() +"'";
    }

    public String getJid() {
	return jid;
    }

    public void setJid(String jid) {
	this.jid = jid;
    }

    public String getAffiliation() {
	return affiliation.toString();
    }

    public void setAffiliation(String affiliation) {
	this.affiliation = Type.valueOf(affiliation);
    }

}
