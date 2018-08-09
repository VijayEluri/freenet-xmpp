package xmpp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.ServiceDiscoveryManager;
import org.jivesoftware.smackx.packet.DiscoverInfo;
import org.jivesoftware.smackx.packet.DiscoverInfo.Identity;
import org.jivesoftware.smackx.provider.DiscoverInfoProvider;

import se.su.it.smack.packet.XMPPElement;
import se.su.it.smack.provider.PubSubEventProvider;
import se.su.it.smack.provider.PubSubProvider;
import se.su.it.smack.pubsub.PubSub;
import se.su.it.smack.pubsub.elements.AffiliationElement;
import se.su.it.smack.pubsub.elements.AffiliationsElement;
import se.su.it.smack.pubsub.elements.ConfigureElement;
import se.su.it.smack.pubsub.elements.CreateElement;
import se.su.it.smack.pubsub.elements.DeleteElement;
import se.su.it.smack.pubsub.elements.ItemElement;
import se.su.it.smack.pubsub.elements.ItemsElement;
import se.su.it.smack.pubsub.elements.PublishElement;
import se.su.it.smack.pubsub.elements.PurgeElement;
import se.su.it.smack.pubsub.elements.SubscribeElement;
import se.su.it.smack.pubsub.elements.SubscriptionsElement;
import se.su.it.smack.utils.XMPPUtils;

public class XMPP2 {
	private XMPPConnection con;

	private DiscoverInfo discoInfo;

	private static final String nodeName = "darknetnodereference";

	private String user;

	private String password;

	public XMPP2(String user, String server, String password, boolean sasl, boolean tls, boolean selfsigned) {
		// System.setProperty("org.apache.commons.logging.Log",
		// "org.apache.commons.logging.impl.SimpleLog");
		// System.setProperty("org.apache.commons.logging.simplelog.defaultlog",
		// "trace");
		this.user = user;
		this.password = password;

		// I can't get it to work with a smack.providers-file. Instead the the
		// providers are loaded manually
		ProviderManager.getInstance()
				.addIQProvider("pubsub", "http://jabber.org/protocol/pubsub", new PubSubProvider());
		ProviderManager.getInstance().addIQProvider("pubsub", "http://jabber.org/protocol/pubsub#owner",
				new PubSubProvider("owner"));
		ProviderManager.getInstance().addIQProvider("query", "http://jabber.org/protocol/disco#info",
				new DiscoverInfoProvider());
		ProviderManager.getInstance().addExtensionProvider("event", "http://jabber.org/protocol/pubsub#event",
				new PubSubEventProvider());

		ConnectionConfiguration config = new ConnectionConfiguration(server);
		if (tls)
			config.setSecurityMode(ConnectionConfiguration.SecurityMode.required);
		config.setSelfSignedCertificateEnabled(selfsigned);
		config.setSASLAuthenticationEnabled(sasl);
		con = new XMPPConnection(config);
	}

	public Roster getRoster() {
		return con.getRoster();
	}

	public void login() throws XMPPException {
		con.connect();
		con.login(user, password);
	}

	public void disconnect() {
		con.disconnect();
	}

	public void purgeNode() throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);
		pubSub.addChild(new PurgeElement(nodeName));

		XMPPUtils.sendAndWait(con, pubSub);
	}

	private void discover() throws XMPPException {
		ServiceDiscoveryManager disco = new ServiceDiscoveryManager(con);
		discoInfo = disco.discoverInfo(con.getServiceName());
	}

	public boolean hasPubSub() throws XMPPException {
		if (discoInfo == null)
			discover();
		return discoInfo.containsFeature("http://jabber.org/protocol/pubsub");
	}

	public boolean hasGoodPubSub() throws XMPPException {
		if (discoInfo == null)
			discover();
		return hasPubSub() && discoInfo.containsFeature("http://jabber.org/protocol/pubsub#create-nodes")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#delete-nodes")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#modify-affiliations")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#persistent-items")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#publish")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#retrieve-items");

	}

	public boolean hasWhitelist() throws XMPPException {
		if (discoInfo == null)
			discover();
		return discoInfo.containsFeature("http://jabber.org/protocol/pubsub#access-whitelist")
				&& discoInfo.containsFeature("http://jabber.org/protocol/pubsub#member-affiliation");
	}

	public boolean hasOpen() throws XMPPException {
		if (discoInfo == null)
			discover();
		return discoInfo.containsFeature("http://jabber.org/protocol/pubsub#access-open");
	}

	public boolean hasPEP() throws XMPPException {
		if (discoInfo == null)
			discover();
		for (Iterator<Identity> iter = discoInfo.getIdentities(); iter.hasNext();)
			if ("pep".equals(iter.next().getType()))
				return true;

		return false;
	}

	public boolean isTigase() throws XMPPException {
		if (discoInfo == null)
			discover();
		for (Iterator<Identity> iter = discoInfo.getIdentities(); iter.hasNext();) {
			Identity identity = iter.next();
			if (identity.getCategory().equals("server") && identity.getType().equals("im")
					&& identity.getName().startsWith("Tigase"))
				return true;
		}
		return false;
	}

	public void config() {
		PubSub pubSub = new PubSub("owner");
		pubSub.setTo("pubsub." + con.getHost());
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);

		ConfigureElement ce = new ConfigureElement(nodeName);
		ce.addField("pubsub#access_model", "whitelist");
		ce.addField("pubsub#persist_items", "1");
		ce.addField("pubsub#subscribe", "0");

		pubSub.addChild(ce);
		System.out.println(pubSub.toXML());
		con.sendPacket(pubSub);
	}

	public void getConfig() throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setFrom(getUser());
		pubSub.addChild(new ConfigureElement(nodeName));
		Packet response = XMPPUtils.sendAndWait(con, pubSub);
		System.out.println(response.toXML());
	}

	public Map<String, String> retrieveAllItems(String user) throws XMPPException {
		PubSub pubSub = new PubSub();
		pubSub.setFrom(getUser());
		if (user != null)
			pubSub.setTo(user);
		pubSub.addChild(new ItemsElement(nodeName));

		PubSub result = (PubSub) XMPPUtils.sendAndWait(con, pubSub);

		Map<String, String> map = new HashMap<String, String>();
		ItemsElement items = (ItemsElement) result.getChild();
		for (XMPPElement element : items.getChildren()) {
			ItemElement item = (ItemElement) element;
			map.put(item.getId(), item.getContent().getTextContent());
		}
		return map;
	}

	public void createNode(String access_model) throws XMPPException {
		PubSub pubSub = new PubSub();
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);

		CreateElement cd = new CreateElement(nodeName);
		ConfigureElement ce = new ConfigureElement();
		ce.addField("pubsub#persist_items", "1");
		ce.addField("pubsub#access_model", access_model);
		// ce.addField("pubsub#subscribe", "0");
		pubSub.addChild(cd);
		pubSub.addChild(ce);

		XMPPUtils.sendAndWait(con, pubSub);
	}

	public Map<String, String> getAffiliates() throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setFrom(getUser());
		pubSub.addChild(new AffiliationsElement(nodeName));

		PubSub result = (PubSub) XMPPUtils.sendAndWait(con, pubSub);

		Map<String, String> map = new HashMap<String, String>();
		AffiliationsElement affiliations = (AffiliationsElement) result.getChild();
		for (XMPPElement element : affiliations.getChildren()) {
			AffiliationElement affiliation = (AffiliationElement) element;
			map.put(affiliation.getJid(), affiliation.getAffiliation());
		}
		return map;
	}

	public void addAffiliates(XMPPConnection con, Map<String, AffiliationElement.Type> affiliations)
			throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);
		AffiliationsElement ae = new AffiliationsElement(nodeName);
		for (Map.Entry<String, AffiliationElement.Type> entry : affiliations.entrySet())
			ae.addChild(new AffiliationElement(entry.getKey(), entry.getValue()));
		pubSub.addChild(ae);

		XMPPUtils.sendAndWait(con, pubSub);
	}

	public void subscribe() throws XMPPException {
		PubSub pubSub = new PubSub();
		pubSub.setFrom(getUser());
		pubSub.setTo("jonasb@jabber.se");
		pubSub.setType(IQ.Type.SET);
		SubscribeElement se = new SubscribeElement(nodeName, "jonas@jabber.rootbash.com");
		pubSub.addChild(se);
		// pubSub.addChild(new SubscriptionsElement(nodeName));
		// System.out.println(pubSub.toXML());

		Packet response = XMPPUtils.sendAndWait(con, pubSub);
		System.out.println(response.toXML());
	}

	public void subscriptions() throws XMPPException {
		PubSub pubSub = new PubSub();
		pubSub.setFrom(getUser());
		pubSub.addChild(new SubscriptionsElement(nodeName));

		XMPPUtils.sendAndWait(con, pubSub);
	}

	public void deleteNode() throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setTo(con.getServiceName());
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);
		pubSub.addChild(new DeleteElement(nodeName));

		XMPPUtils.sendAndWait(con, pubSub);
	}

	public void configure() throws XMPPException {
		PubSub pubSub = new PubSub("owner");
		pubSub.setFrom(getUser());
		pubSub.setTo(con.getHost());
		pubSub.setType(IQ.Type.SET);
		ConfigureElement ce = new ConfigureElement(nodeName);
		ce.addField("pubsub#access_model", "open");
		pubSub.addChild(ce);

		XMPPUtils.sendAndWait(con, pubSub);
	}

	public void publish(String id, String reference) throws XMPPException {
		PubSub pubSub = new PubSub();
		pubSub.setFrom(getUser());
		pubSub.setType(IQ.Type.SET);
		PublishElement pe = new PublishElement(nodeName);
		ItemElement ie = new ItemElement(id, "<reference>" + reference + "</reference>");
		pe.addChild(ie);
		pubSub.addChild(pe);

		XMPPUtils.sendAndWait(con, pubSub);
	}

	private String getUser() {
		// con.getUser() doesn't work for gmail since the server and the
		// serviceName is different
		return user + "@" + con.getServiceName() + "/Smack";
	}
}
