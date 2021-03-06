package com.hypersocket.properties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.hypersocket.encrypt.EncryptionService;
import com.hypersocket.resource.AbstractResource;

@Repository
public abstract class ResourceTemplateRepositoryImpl extends
		PropertyRepositoryImpl implements ResourceTemplateRepository {

	static Logger log = LoggerFactory
			.getLogger(ResourceTemplateRepositoryImpl.class);

	DatabasePropertyStore configPropertyStore;

	@Autowired
	EncryptionService encryptionService;

	Map<String, PropertyCategory> activeCategories = new HashMap<String, PropertyCategory>();
	Map<String, PropertyTemplate> propertyTemplates = new HashMap<String, PropertyTemplate>();
	Map<String, PropertyStore> propertyStoresByResourceKey = new HashMap<String, PropertyStore>();
	Map<String, PropertyStore> propertyStoresById = new HashMap<String, PropertyStore>();
	Set<PropertyStore> propertyStores = new HashSet<PropertyStore>();

	List<PropertyTemplate> activeTemplates = new ArrayList<PropertyTemplate>();
	Set<String> propertyNames = new HashSet<String>();
	Set<String> variableNames = new HashSet<String>();
	
	Set<PropertyResolver> propertyResolvers = new HashSet<PropertyResolver>();
	String resourceXmlPath;

	static Map<String, List<ResourceTemplateRepository>> propertyContexts = new HashMap<String, List<ResourceTemplateRepository>>();

	public ResourceTemplateRepositoryImpl() {
		super();
	}

	@PostConstruct
	private void postConstruct() {
		configPropertyStore = new DatabasePropertyStore(this, encryptionService);
		propertyStoresById.put("db", configPropertyStore);
	}
	
	public ResourceTemplateRepositoryImpl(boolean requiresDemoWrite) {
		super(requiresDemoWrite);
	}

	@Override
	public ResourcePropertyStore getDatabasePropertyStore() {
		return configPropertyStore;
	}
	
	protected ResourcePropertyStore getPropertyStore() {
		return configPropertyStore;
	}

	@Override
	public void registerPropertyResolver(PropertyResolver resolver) {
		propertyResolvers.add(resolver);
	}
	
	@Override
	public Set<String> getPropertyNames(AbstractResource resource) {
		
		Set<String> results = new HashSet<String>();
		results.addAll(propertyNames);
		for(PropertyResolver r : propertyResolvers) {
			results.addAll(r.getPropertyNames(resource));
		}
		return results;
	}
	
	@Override
	public Set<String> getPropertyNames(AbstractResource resource, boolean includeResolvers) {
		
		Set<String> results = new HashSet<String>();
		results.addAll(propertyNames);
		if(includeResolvers) {
			for(PropertyResolver r : propertyResolvers) {
				results.addAll(r.getPropertyNames(resource));
			}
		}
		return results;
	}

	@Override
	public Set<String> getVariableNames(AbstractResource resource) {
		
		Set<String> results = new HashSet<String>();
		results.addAll(variableNames);
		for(PropertyResolver r : propertyResolvers) {
			results.addAll(r.getVariableNames(resource));
		}
		return results;
	}

	public static Set<String> getContextNames() {
		return propertyContexts.keySet();
	}

	public void loadPropertyTemplates(String resourceXmlPath) {

		this.resourceXmlPath = resourceXmlPath;
		

		String context = null;
		try {
			Enumeration<URL> urls = getClass().getClassLoader().getResources(
					resourceXmlPath);
			if (!urls.hasMoreElements()) {
				throw new IllegalArgumentException(resourceXmlPath
						+ " does not exist!");
			}

			while (urls.hasMoreElements()) {
				URL url = urls.nextElement();
				try {
					context = loadPropertyTemplates(url);
				} catch (Exception e) {
					log.error("Failed to process " + url.toExternalForm(), e);
				}
			}

		} catch (IOException e) {
			log.error("Failed to load propertyTemplate.xml resources", e);
		}

		if (context != null) {

			if (log.isInfoEnabled()) {
				log.info("Loading attributes for context " + context);
			}
			if (!propertyContexts.containsKey(context)) {
				propertyContexts.put(context,
						new ArrayList<ResourceTemplateRepository>());
			}
			propertyContexts.get(context).add(this);
		}

	}

	private void loadPropertyStores(Document doc) {

		NodeList list = doc.getElementsByTagName("propertyStore");

		for (int i = 0; i < list.getLength(); i++) {
			Element node = (Element) list.item(i);
			try {
				@SuppressWarnings("unchecked")
				Class<? extends PropertyStore> clz = (Class<? extends PropertyStore>) Class
						.forName(node.getAttribute("type"));

				PropertyStore store = clz.newInstance();
				
				if(store instanceof XmlTemplatePropertyStore) {
					((XmlTemplatePropertyStore)store).init(node);
				}

				propertyStoresById.put(node.getAttribute("id"), store);
				propertyStores.add(store);
			} catch (Throwable e) {
				log.error("Failed to parse remote extension definition", e);
			}
		}

	}

	private String loadPropertyTemplates(URL url) throws SAXException,
			IOException, ParserConfigurationException {

		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory
				.newInstance();
		DocumentBuilder xmlBuilder = xmlFactory.newDocumentBuilder();
		Document doc = xmlBuilder.parse(url.openStream());

		Element root = doc.getDocumentElement();
		String context = null;

		if (root.hasAttribute("extends")) {
			String extendsTemplates = root.getAttribute("extends");
			StringTokenizer t = new StringTokenizer(extendsTemplates, ",");
			while (t.hasMoreTokens()) {
				Enumeration<URL> extendUrls = getClass().getClassLoader()
						.getResources(t.nextToken());
				while (extendUrls.hasMoreElements()) {
					URL extendUrl = extendUrls.nextElement();
					try {
						context = loadPropertyTemplates(extendUrl);
					} catch (Exception e) {
						log.error(
								"Failed to process "
										+ extendUrl.toExternalForm(), e);
					}
				}
			}
		} else if (root.hasAttribute("context")) {
			context = root.getAttribute("context");
		}

		if (log.isInfoEnabled()) {
			log.info("Loading property template resource "
					+ url.toExternalForm());
		}

		loadPropertyStores(doc);

		loadPropertyCategories(doc);

		return context;
	}



	private void loadPropertyCategories(Document doc) throws IOException {

		NodeList list = doc.getElementsByTagName("propertyCategory");

		for (int i = 0; i < list.getLength(); i++) {
			Element node = (Element) list.item(i);

			if (!node.hasAttribute("resourceKey")
					|| !node.hasAttribute("resourceBundle")
					|| !node.hasAttribute("weight")) {
				throw new IOException(
						"<propertyCategory> requires resourceKey, resourceBundle and weight attributes");
			}

			if (log.isInfoEnabled()) {
				log.info("Registering category "
						+ node.getAttribute("resourceKey") + " for bundle "
						+ node.getAttribute("resourceBundle"));
			}

			String group = "system";
			if (node.hasAttribute("group")) {
				group = node.getAttribute("group");
			}

			PropertyCategory cat = registerPropertyCategory(
					node.getAttribute("resourceKey"),
					node.getAttribute("resourceBundle"),
					Integer.parseInt(node.getAttribute("weight")), false,
					group, node.getAttribute("displayMode"));

			PropertyStore defaultStore = getPropertyStore();
			
			if(node.hasAttribute("store")) {
				defaultStore = propertyStoresById.get(node.getAttribute("store"));
				if (defaultStore == null) {
					throw new IOException("PropertyStore "
							+ node.getAttribute("store")
							+ " does not exist!");
				}
			}
			
			NodeList properties = node.getElementsByTagName("property");

			for (int x = 0; x < properties.getLength(); x++) {

				Element pnode = (Element) properties.item(x);

				if (!pnode.hasAttribute("resourceKey")) {
					throw new IOException(
							"property must have a resourceKey attribute");
				}
				try {

					PropertyTemplate t = propertyTemplates.get(pnode.getAttribute("resourceKey"));
					if (t != null) {
						if (log.isInfoEnabled()) {
							log.info("Overriding default value of "
									+ t.getResourceKey() + " to "
									+ pnode.getAttribute("defaultValue"));
						}
						t.setDefaultValue(pnode.getAttribute("defaultValue"));
						continue;
					}

					PropertyStore store = defaultStore;
					if (pnode.hasAttribute("store")) {
						store = propertyStoresById.get(pnode
								.getAttribute("store"));
						if (store == null) {
							throw new IOException("PropertyStore "
									+ pnode.getAttribute("store")
									+ " does not exist!");
						}
					}

					registerPropertyItem(
							cat,
							store,
							pnode.getAttribute("resourceKey"),
							generateMetaData(pnode),
							pnode.getAttribute("mapping"),
							Integer.parseInt(pnode.getAttribute("weight")),
							pnode.hasAttribute("hidden")
									&& pnode.getAttribute("hidden")
											.equalsIgnoreCase("true"),
							pnode.hasAttribute("displayMode") ? pnode.getAttribute("displayMode") : "",
							pnode.hasAttribute("readOnly")
									&& pnode.getAttribute("readOnly")
											.equalsIgnoreCase("true"),
							Boolean.getBoolean("hypersocket.development")
									&& pnode.hasAttribute("developmentValue") ? pnode
									.getAttribute("developmentValue") : pnode
									.getAttribute("defaultValue"),
							pnode.hasAttribute("variable")
									&& pnode.getAttribute("variable")
											.equalsIgnoreCase("true"),
							pnode.hasAttribute("encrypted")
									&& pnode.getAttribute("encrypted")
											.equalsIgnoreCase("true"),
							pnode.hasAttribute("defaultsToProperty")
									? pnode.getAttribute("defaultsToProperty")
									: null);
				} catch (Throwable e) {
					log.error("Failed to register property item", e);
				}
			}
		}
	}

	private String generateMetaData(Element pnode) {
		NamedNodeMap map = pnode.getAttributes();
		StringBuffer buf = new StringBuffer();
		buf.append("{");
		for (int i = 0; i < map.getLength(); i++) {
			Node n = map.item(i);
			if (buf.length() > 1) {
				buf.append(", ");
			}
			buf.append("\"");
			buf.append(n.getNodeName());
			buf.append("\": ");
			if (n.getNodeName().equals("options")) {
				buf.append("[");
				StringTokenizer t = new StringTokenizer(n.getNodeValue(), ",");
				while (t.hasMoreTokens()) {
					String opt = t.nextToken();
					buf.append("{ \"name\": \"");
					buf.append(opt);
					buf.append("\", \"value\": \"");
					buf.append(opt);
					buf.append("\"}");
					if (t.hasMoreTokens()) {
						buf.append(",");
					}
				}
				buf.append("]");
			} else {
				buf.append("\"");
				buf.append(n.getNodeValue());
				buf.append("\"");
			}
		}
		buf.append("}");
		return buf.toString();
	}

	@Override
	public PropertyTemplate getPropertyTemplate(AbstractResource resource, String resourceKey) {
		
		for(PropertyResolver r : propertyResolvers) {
			if(r.hasPropertyTemplate(resource, resourceKey)) {
				return r.getPropertyTemplate(resource, resourceKey);
			}
		}
		
		return propertyTemplates.get(resourceKey);
	}

	private void registerPropertyItem(PropertyCategory category,
			PropertyStore propertyStore, String resourceKey, String metaData,
			String mapping, int weight, boolean hidden, String displayMode, boolean readOnly,
			String defaultValue, boolean isVariable, boolean encrypted,
			String defaultsToProperty) {

		if (log.isInfoEnabled()) {
			log.info("Registering property " + resourceKey);
		}

		if (defaultValue != null && defaultValue.startsWith("classpath:")) {
			String url = defaultValue.substring(10);
			InputStream in = getClass().getResourceAsStream(url);
			try {
				if (in != null) {
					try {
						defaultValue = IOUtils.toString(in);
					} catch (IOException e) {
						log.error(
								"Failed to load default value classpath resource "
										+ defaultValue, e);
					}
				} else {
					log.error("Failed to load default value classpath resource "
							+ url);
				}
			} finally {
				IOUtils.closeQuietly(in);
			}
		}

		propertyNames.add(resourceKey);

		if (isVariable) {
			variableNames.add(resourceKey);
		}
		PropertyTemplate template = propertyStore
				.getPropertyTemplate(resourceKey);
		if (template == null) {
			template = new PropertyTemplate();
			template.setResourceKey(resourceKey);
		}

		template.setMetaData(metaData);
		template.setDefaultValue(defaultValue);
		template.setWeight(weight);
		template.setHidden(hidden);
		template.setDisplayMode(displayMode);
		template.setReadOnly(readOnly);
		template.setMapping(mapping);
		template.setCategory(category);
		template.setEncrypted(encrypted);
		template.setDefaultsToProperty(defaultsToProperty);
		template.setPropertyStore(propertyStore);

		propertyStore.registerTemplate(template, resourceXmlPath);
		category.getTemplates().add(template);
		activeTemplates.add(template);
		propertyTemplates.put(resourceKey, template);

		Collections.sort(category.getTemplates(),
				new Comparator<AbstractPropertyTemplate>() {
					@Override
					public int compare(AbstractPropertyTemplate cat1,
							AbstractPropertyTemplate cat2) {
						return cat1.getWeight().compareTo(cat2.getWeight());
					}
				});

		Collections.sort(activeTemplates,
				new Comparator<AbstractPropertyTemplate>() {
					@Override
					public int compare(AbstractPropertyTemplate t1,
							AbstractPropertyTemplate t2) {
						return t1.getResourceKey().compareTo(
								t2.getResourceKey());
					}
				});

	}

	private PropertyCategory registerPropertyCategory(String resourceKey,
			String bundle, int weight, boolean userCreated, String group,
			String displayMode) {

		if (activeCategories.containsKey(resourceKey)) {
			PropertyCategory cat = activeCategories.get(resourceKey);
			if (cat.getBundle().equals(bundle)) {
				return cat;
			}
			throw new IllegalStateException("Cannot register " + resourceKey
					+ "/" + bundle
					+ " as the resource key is already registered by bundle "
					+ activeCategories.get(resourceKey).getBundle());
		}

		if (activeCategories.containsKey(resourceKey)) {
			return activeCategories.get(resourceKey);
		}

		PropertyCategory category = new PropertyCategory();
		category.setBundle(bundle);
		category.setCategoryKey(resourceKey);
		category.setCategoryGroup(group);
		category.setDisplayMode(displayMode);
		category.setWeight(weight);
		category.setUserCreated(userCreated);

		activeCategories.put(category.getCategoryKey(), category);
		return category;
	}

	@Override
	public String getValue(AbstractResource resource, String resourceKey) {
		return getValue(resource, resourceKey, null);
	}
	
	@Override
	public String getValue(AbstractResource resource, String resourceKey, String defaultValue) {

		PropertyTemplate template = propertyTemplates.get(resourceKey);

		if (template == null) {
			
			for(PropertyResolver r : propertyResolvers) {
				template = r.getPropertyTemplate(resource, resourceKey);
				if(template!=null) {
					break;
				}
			}
			
			if(template==null) {
				return configPropertyStore.getProperty(resource, resourceKey, defaultValue);
			}
		}

		return ((ResourcePropertyStore) template.getPropertyStore())
				.getPropertyValue(template, resource);
	}

	@Override
	public Integer getIntValue(AbstractResource resource, String name)
			throws NumberFormatException {
		return Integer.parseInt(getValue(resource, name));
	}

	
	@Override
	public Long getLongValue(AbstractResource resource, String name)
			throws NumberFormatException {
		return Long.parseLong(getValue(resource, name));
	}

	@Override
	public Date getDateValue(AbstractResource resource, String name)
			throws NumberFormatException {
		return new Date(getLongValue(resource, name));
	}
	
	@Override
	public Double getDoubleValue(AbstractResource resource, String resourceKey) {
		return Double.parseDouble(getValue(resource, resourceKey));
	}
	
	@Override
	public void setDoubleValue(AbstractResource resource, String resourceKey, Double value) {
		setValue(resource, resourceKey, Double.toString(value));
	}

	@Override
	public void setValue(AbstractResource resource, String name, Long value) {
		setValue(resource, name, Long.toString(value));
	}
	
	@Override
	public void setValue(AbstractResource resource, String name, Date value) {
		setValue(resource, name, value.getTime());
	}
	
	@Override
	public Boolean getBooleanValue(AbstractResource resource, String name) {
		return Boolean.parseBoolean(getValue(resource, name));
	}

	@Override
	public boolean hasPropertyTemplate(AbstractResource resource, String key) {
		return propertyTemplates.containsKey(key);
	}
	
	@Override 
	public boolean hasPropertyValueSet(AbstractResource resource, String resourceKey) {
		PropertyTemplate template = getPropertyTemplate(resource, resourceKey);
		return ((ResourcePropertyStore)template.getPropertyStore()).hasPropertyValueSet(template, resource);
	}

	@Override
	public void setValues(AbstractResource resource,
			Map<String, String> properties) {

		if(properties!=null) {
			for (String resourceKey : properties.keySet()) {
				if(propertyTemplates.containsKey(resourceKey)) {
					setValue(resource, resourceKey, properties.get(resourceKey));
				}
			}
		}
	}

	@Override
	public void setValue(AbstractResource resource, String resourceKey,
			String value) {

		PropertyTemplate template = propertyTemplates.get(resourceKey);

		if (template == null) {
			
			for(PropertyResolver r : propertyResolvers) {
				template = r.getPropertyTemplate(resource, resourceKey);
				if(template!=null) {
					break;
				}
			}
			
			if(template==null) {
				if(resource==null) {
					System.out.println();
				}
				if(resourceKey==null) {
					System.out.println();
				}
				if(value==null) {
					System.out.println();
				}
				configPropertyStore.setProperty(resource, resourceKey, value);
				return;
			}
		}

		if (template.isReadOnly()) {
			return;
		}

		((ResourcePropertyStore) template.getPropertyStore()).setPropertyValue(
				template, resource, value);
	}

	@Override
	public void setValue(AbstractResource resource, String resourceKey,
			Integer value) {
		setValue(resource, resourceKey, String.valueOf(value));
	}

	@Override
	public void setValue(AbstractResource resource, String name, Boolean value) {
		setValue(resource, name, String.valueOf(value));
	}

	@Override
	public Collection<PropertyCategory> getPropertyCategories(
			AbstractResource resource) {

		List<PropertyCategory> cats = new ArrayList<PropertyCategory>();
		
		for (PropertyCategory c : activeCategories.values()) {
			PropertyCategory tmp = new PropertyCategory();
			tmp.setBundle(c.getBundle());
			tmp.setCategoryKey(c.getCategoryKey());
			tmp.setWeight(c.getWeight());
			tmp.setDisplayMode(c.getDisplayMode());
			tmp.setUserCreated(c.isUserCreated());
			for (AbstractPropertyTemplate t : c.getTemplates()) {
				tmp.getTemplates().add(new ResourcePropertyTemplate(t, resource));
			}
			cats.add(tmp);
		}
		
		for(PropertyResolver r : propertyResolvers) {
			for(PropertyCategory c : r.getPropertyCategories(resource)) {
				PropertyCategory tmp = new PropertyCategory();
				tmp.setBundle(c.getBundle());
				tmp.setCategoryKey(c.getCategoryKey());
				tmp.setWeight(c.getWeight());
				tmp.setDisplayMode(c.getDisplayMode());
				tmp.setUserCreated(c.isUserCreated());
				for (AbstractPropertyTemplate t : c.getTemplates()) {
					tmp.getTemplates().add(new ResourcePropertyTemplate(t, resource));
				}
				cats.add(tmp);
			}
		}

		Collections.sort(cats, new Comparator<PropertyCategory>() {
			@Override
			public int compare(PropertyCategory cat1, PropertyCategory cat2) {
				return cat1.getWeight().compareTo(cat2.getWeight());
			}
		});

		return cats;
	}

	@Override
	public Collection<PropertyCategory> getPropertyCategories(
			AbstractResource resource, String group) {

		List<PropertyCategory> cats = new ArrayList<PropertyCategory>();
		for (PropertyCategory c : activeCategories.values()) {
			if (!c.getCategoryGroup().equals(group)) {
				continue;
			}
			PropertyCategory tmp = new PropertyCategory();
			tmp.setBundle(c.getBundle());
			tmp.setCategoryKey(c.getCategoryKey());
			tmp.setWeight(c.getWeight());
			tmp.setUserCreated(c.isUserCreated());
			tmp.setDisplayMode(c.getDisplayMode());
			for (AbstractPropertyTemplate t : c.getTemplates()) {
				tmp.getTemplates().add(
							new ResourcePropertyTemplate(t, resource));
			}
			cats.add(tmp);
		}

		for(PropertyResolver r : propertyResolvers) {
			for(PropertyCategory c : r.getPropertyCategories(resource)) {
				if (!c.getCategoryGroup().equals(group)) {
					continue;
				}
				PropertyCategory tmp = new PropertyCategory();
				tmp.setBundle(c.getBundle());
				tmp.setCategoryKey(c.getCategoryKey());
				tmp.setWeight(c.getWeight());
				tmp.setDisplayMode(c.getDisplayMode());
				tmp.setUserCreated(c.isUserCreated());
				for (AbstractPropertyTemplate t : c.getTemplates()) {
					tmp.getTemplates().add(new ResourcePropertyTemplate(t, resource));
				}
				cats.add(tmp);
			}
		}
		Collections.sort(cats, new Comparator<PropertyCategory>() {
			@Override
			public int compare(PropertyCategory cat1, PropertyCategory cat2) {
				return cat1.getWeight().compareTo(cat2.getWeight());
			}
		});

		return cats;
	}

	@Override
	public Collection<PropertyTemplate> getPropertyTemplates(AbstractResource resource) {
		
		Set<PropertyTemplate> results = new HashSet<PropertyTemplate>();
		results.addAll(activeTemplates);
		for(PropertyResolver r : propertyResolvers) {
			results.addAll(r.getPropertyTemplates(resource));
		}
		return Collections.unmodifiableCollection(results);
	}
	
	@Override
	public Map<String,PropertyTemplate> getRepositoryTemplates() {
		Map<String,PropertyTemplate> result = new HashMap<String,PropertyTemplate>();
		for(PropertyTemplate t : activeTemplates) {
			result.put(t.getResourceKey(), t);
		}
		return result;
	}

	@Override
	public String[] getValues(AbstractResource resource, String name) {

		String values = getValue(resource, name);
		return ResourceUtils.explodeValues(values);
	}

	@Override
	public Map<String, String> getProperties(AbstractResource resource) {

		Map<String, String> properties = new HashMap<String, String>();
		for (String name : getPropertyNames(resource)) {
			if(propertyTemplates.containsKey(name)) {
				properties.put(name, getValue(resource, name));
			}
		}
		return properties;
	}

}
