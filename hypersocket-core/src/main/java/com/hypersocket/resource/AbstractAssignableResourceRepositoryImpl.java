/*******************************************************************************
 * Copyright (c) 2013 Hypersocket Limited.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 ******************************************************************************/
package com.hypersocket.resource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.FetchMode;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hypersocket.properties.EntityResourcePropertyStore;
import com.hypersocket.properties.PropertyTemplate;
import com.hypersocket.properties.ResourcePropertyStore;
import com.hypersocket.properties.ResourceTemplateRepositoryImpl;
import com.hypersocket.realm.Principal;
import com.hypersocket.realm.Realm;
import com.hypersocket.realm.RealmRestriction;
import com.hypersocket.repository.CriteriaConfiguration;
import com.hypersocket.repository.DeletedCriteria;
import com.hypersocket.repository.DeletedDetachedCriteria;
import com.hypersocket.session.Session;
import com.hypersocket.tables.ColumnSort;
import com.hypersocket.tables.Sort;

@Repository
@Transactional
public abstract class AbstractAssignableResourceRepositoryImpl<T extends AssignableResource>
		extends ResourceTemplateRepositoryImpl implements
		AbstractAssignableResourceRepository<T> {

	@Autowired
	EntityResourcePropertyStore entityPropertyStore;

	@Override
	public Collection<T> getAssignedResources(List<Principal> principals) {
		return getAssignedResources(principals.toArray(new Principal[0]));
	}

	@Override
	protected ResourcePropertyStore getPropertyStore() {
		return entityPropertyStore;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<T> getAssignedResources(Principal... principals) {

		
		Criteria criteria = createCriteria(getResourceClass());
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		
		criteria.add(Restrictions.eq("realm", principals[0].getRealm()));
		
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", true));
		
		Set<T> everyone = new HashSet<T>(criteria.list());
		
		Set<Long> ids = new HashSet<Long>();
		for (Principal p : principals) {
			ids.add(p.getId());
		}

		criteria = createCriteria(getResourceClass());
		
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		
		criteria.add(Restrictions.eq("realm", principals[0].getRealm()));

		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", false));
		criteria = criteria.createCriteria("principals");
		criteria.add(Restrictions.in("id", ids));
		
		everyone.addAll((List<T>) criteria.list());
		return everyone;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Collection<T> searchAssignedResources(List<Principal> principals,
			final String searchPattern, final int start, final int length,
			final ColumnSort[] sorting, CriteriaConfiguration... configs) {

		Criteria criteria = createCriteria(getResourceClass());
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		
		if (StringUtils.isNotBlank(searchPattern)) {
			criteria.add(Restrictions.ilike("name", searchPattern));
		}

		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}

		criteria.add(Restrictions.eq("realm", principals.get(0).getRealm()));
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", true));
		
		Set<T> everyone = new HashSet<T>(criteria.list());

		criteria = createCriteria(getResourceClass());
		
		ProjectionList projList = Projections.projectionList();
		projList.add(Projections.property("id"));
		projList.add(Projections.property("name"));
		
		criteria.setProjection(Projections.distinct(projList));
		criteria.setFirstResult(start);
		criteria.setMaxResults(length);
		
		if (StringUtils.isNotBlank(searchPattern)) {
			criteria.add(Restrictions.ilike("name", searchPattern));
		}

		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}
		
		for (ColumnSort sort : sorting) {
			criteria.addOrder(sort.getSort() == Sort.ASC ? Order.asc(sort
					.getColumn().getColumnName()) : Order.desc(sort.getColumn()
					.getColumnName()));
		}
		
		criteria.add(Restrictions.eq("realm", principals.get(0).getRealm()));

		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", false));
		criteria = criteria.createCriteria("principals");
		
		List<Long> ids = new ArrayList<Long>();
		for(Principal p : principals) {
			ids.add(p.getId());
		}
		criteria.add(Restrictions.in("id", ids));
		
		List<Object[]> results = (List<Object[]>)criteria.list();
		
		if(results.size() > 0) {
			Long[] entityIds = new Long[results.size()];
			int idx = 0;
			for(Object[] obj : results) {
				entityIds[idx++] = (Long) obj[0];
			}
			
			criteria = createCriteria(getResourceClass());
			criteria.add(Restrictions.in("id", entityIds));
	
			everyone.addAll((List<T>) criteria.list());
		}
		return everyone;
	};

	@Override
	public boolean hasAssignedEveryoneRole(Realm realm, CriteriaConfiguration... configs) {

		Criteria criteria = createCriteria(getResourceClass());
		criteria.setProjection(Projections.property("id"));
		criteria.setResultTransformer(CriteriaSpecification.PROJECTION);

		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}

		criteria.add(Restrictions.eq("realm", realm));
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", true));
		
		List<?> everyoneRoles = criteria.list();
		
		return everyoneRoles.size() > 0;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public Collection<Principal> getAssignedPrincipals(Realm realm, CriteriaConfiguration... configs) {

		Criteria criteria = createCriteria(getResourceClass());
		
		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}

		criteria.add(Restrictions.eq("realm", realm));
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", false));
		
		criteria = criteria.createCriteria("principals");
		criteria.setProjection(Projections.distinct(Projections.property("id")));
		criteria.setResultTransformer(CriteriaSpecification.PROJECTION);
		
		List<?> uniquePrincipals = criteria.list();
		
		if(uniquePrincipals.isEmpty()) {
			return new HashSet<Principal>();
		}
		criteria = createCriteria(Principal.class);
		criteria.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		criteria.add(Restrictions.in("id", uniquePrincipals));
		List<?> res = criteria.list();
		
		return (Collection<Principal>) res;
	}
		
	@Override
	public Long getAssignedPrincipalCount(Realm realm, CriteriaConfiguration... configs) {

		Criteria criteria = createCriteria(getResourceClass());
		
		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}

		criteria.add(Restrictions.eq("realm", realm));
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", false));
		
		criteria = criteria.createCriteria("principals");
		criteria.setProjection(Projections.distinct(Projections.property("id")));
		criteria.setResultTransformer(CriteriaSpecification.PROJECTION);
		
		List<?> uniquePrincipals = criteria.list();
		
		return (long)uniquePrincipals.size();

	}
	
	@Override
	public Long getAssignedResourceCount(List<Principal> principals,
			final String searchPattern, CriteriaConfiguration... configs) {

		Criteria criteria = createCriteria(getResourceClass());
		criteria.setProjection(Projections.property("id"));
		criteria.setResultTransformer(CriteriaSpecification.PROJECTION);
		if (StringUtils.isNotBlank(searchPattern)) {
			criteria.add(Restrictions.ilike("name", searchPattern));
		}

		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}

		criteria.add(Restrictions.eq("realm", principals.get(0).getRealm()));
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", true));
		
		List<?> ids = criteria.list();
		
		criteria = createCriteria(getResourceClass());
		criteria.setProjection(Projections.countDistinct("name"));
		criteria.setResultTransformer(CriteriaSpecification.PROJECTION);
		if (StringUtils.isNotBlank(searchPattern)) {
			criteria.add(Restrictions.ilike("name", searchPattern));
		}

		for (CriteriaConfiguration c : configs) {
			c.configure(criteria);
		}
		
		criteria.add(Restrictions.eq("realm",  principals.get(0).getRealm()));
		if(ids.size() > 0) {
			criteria.add(Restrictions.not(Restrictions.in("id", ids)));
		}
		
		criteria = criteria.createCriteria("roles");
		criteria.add(Restrictions.eq("allUsers", false));
		criteria = criteria.createCriteria("principals");
		List<Long> pids = new ArrayList<Long>();
		for(Principal p : principals) {
			pids.add(p.getId());
		}
		criteria.add(Restrictions.in("id", pids));
		
		Long count = (Long) criteria.uniqueResult();
		return count + ids.size();

	}

	@Override
	public Long getAssignableResourceCount(List<Principal> principals) {
		return getAssignedResourceCount(principals, "");
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<AssignableResource> getAllAssignableResources(
			List<Principal> principals) {

		Set<Long> ids = new HashSet<Long>();
		for (Principal p : principals) {
			ids.add(p.getId());
		}
		Criteria crit = createCriteria(AssignableResource.class);
		crit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		crit = crit.createCriteria("roles");
		crit = crit.createCriteria("principals");
		crit.add(Restrictions.in("id", ids));

		return (List<AssignableResource>) crit.list();
	}

	@SuppressWarnings("unchecked")
	@Override
	public T getResourceByIdAndPrincipals(Long resourceId,
			List<Principal> principals) {

		Set<Long> ids = new HashSet<Long>();
		for (Principal p : principals) {
			ids.add(p.getId());
		}
		Criteria crit = createCriteria(getResourceClass());
		crit.add(Restrictions.eq("id", resourceId));
		crit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		crit = crit.createCriteria("roles");
		crit = crit.createCriteria("principals");
		crit.add(Restrictions.in("id", ids));

		return (T) crit.uniqueResult();
	}

	protected <K extends AssignableResourceSession<T>> K createResourceSession(
			T resource, Session session, K newSession) {

		newSession.setSession(session);
		newSession.setResource(resource);

		save(newSession);

		return newSession;
	}

	@Override
	public T getResourceByName(String name, Realm realm) {
		return get("name", name, getResourceClass(), new DeletedDetachedCriteria(false), new RealmRestriction(realm));
	}

	@Override
	public T getResourceByName(String name, Realm realm, boolean deleted) {
		return get("name", name, getResourceClass(), new DeletedDetachedCriteria(
				deleted), new RealmRestriction(realm));
	}

	@Override
	public T getResourceById(Long id) {
		return get("id", id, getResourceClass());
	}

	@Override
	public void deleteResource(T resource, @SuppressWarnings("unchecked") TransactionOperation<T>... ops) throws ResourceChangeException {
		beforeDelete(resource);
		for(TransactionOperation<T> op : ops) {
			op.beforeOperation(resource, null);
		}
		delete(resource);
		afterDelete(resource);
		for(TransactionOperation<T> op : ops) {
			op.afterOperation(resource, null);
		}
	}

	protected void beforeDelete(T resource) throws ResourceChangeException {
		
	}
	
	protected void afterDelete(T resource) throws ResourceChangeException {
		
	}
	
	protected void beforeSave(T resource, Map<String,String> properties) throws ResourceChangeException {
		
	}
	
	protected void afterSave(T resource, Map<String,String> properties) throws ResourceChangeException {
		
	}
	
	@Override
	public void populateEntityFields(T resource, Map<String,String> properties) {
		
		for(String resourceKey : getPropertyNames(resource)) {
			if(properties.containsKey(resourceKey)) {
				PropertyTemplate template = getPropertyTemplate(resource, resourceKey);
				if(template.getPropertyStore() instanceof EntityResourcePropertyStore) {
					setValue(resource, resourceKey, properties.get(resourceKey));
					properties.remove(resourceKey);
				}
			}
		}
	}
	
	@Override
	@SafeVarargs
	@Transactional
	public final void saveResource(T resource, Map<String, String> properties, TransactionOperation<T>... ops) throws ResourceChangeException {

		
		beforeSave(resource, properties);
		
		for(TransactionOperation<T> op : ops) {
			op.beforeOperation(resource, properties);
		}

		save(resource);

		// Now set any remaining values
		setValues(resource, properties);
		
		afterSave(resource, properties);
		
		for(TransactionOperation<T> op : ops) {
			op.afterOperation(resource, properties);
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public List<T> getResources(Realm realm) {

		Criteria crit = createCriteria(getResourceClass());
		crit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		crit.setFetchMode("roles", FetchMode.SELECT);
		crit.add(Restrictions.eq("deleted", false));
		crit.add(Restrictions.eq("realm", realm));

		return (List<T>) crit.list();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<T> allResources() {

		Criteria crit = createCriteria(getResourceClass());
		crit.setResultTransformer(CriteriaSpecification.DISTINCT_ROOT_ENTITY);
		crit.setFetchMode("roles", FetchMode.SELECT);
		crit.add(Restrictions.eq("deleted", false));

		return (List<T>) crit.list();
	}

	@Override
	public List<T> search(Realm realm, String searchPattern, int start,
			int length, ColumnSort[] sorting, CriteriaConfiguration... configs) {
		return super.search(getResourceClass(), "name", searchPattern, start,
				length, sorting, ArrayUtils.addAll(configs, new DeletedCriteria(false),
						new RoleSelectMode(), new RealmCriteria(
								realm)));
	}
	
	
	@Override
	public long allRealmsResourcesCount() {
		return getCount(getResourceClass(), new DeletedCriteria(false));
	}

	@Override
	public long getResourceCount(Realm realm, String searchPattern,
			CriteriaConfiguration... configs) {
		return getCount(getResourceClass(), "name", searchPattern,
				ArrayUtils.addAll(configs, new RoleSelectMode(), new DeletedCriteria(false),
						new RealmCriteria(realm)));
	}

	protected abstract Class<T> getResourceClass();

	class RoleSelectMode implements CriteriaConfiguration {

		@Override
		public void configure(Criteria criteria) {
			criteria.setFetchMode("roles", FetchMode.SELECT);
		}
	}

}
