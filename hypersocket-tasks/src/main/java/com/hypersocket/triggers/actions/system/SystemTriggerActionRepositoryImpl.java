package com.hypersocket.triggers.actions.system;

import javax.annotation.PostConstruct;

import com.hypersocket.properties.ResourceTemplateRepositoryImpl;

public class SystemTriggerActionRepositoryImpl extends
		ResourceTemplateRepositoryImpl implements SystemTriggerActionRepository {
	
	@PostConstruct
	private void postConstruct() {
		loadPropertyTemplates("tasks/systemRestart.xml");
	}
}
