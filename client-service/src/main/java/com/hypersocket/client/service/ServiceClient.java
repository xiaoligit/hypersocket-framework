package com.hypersocket.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientTransport;
import com.hypersocket.client.Prompt;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.ResourceService;

public class ServiceClient extends HypersocketClient<Connection> {

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);
	
	ClientServiceImpl service;
	ResourceService resourceService;
	
	List<ServicePlugin> plugins = new ArrayList<ServicePlugin>();
	
	protected ServiceClient(HypersocketClientTransport transport,
			Locale currentLocale, ClientServiceImpl service, ResourceService resourceService, Connection connection) throws IOException {
		super(transport, currentLocale, service);
		this.service = service;
		this.resourceService = resourceService;
		setAttachment(connection);
	}

	@Override
	protected void onDisconnect() {
		// Do nothing cause the listener now handles this
	}

	// @Override
	protected Map<String, String> showLogin(List<Prompt> prompts) throws IOException {
		if(service.getGUI()!=null) {
			try {
				return service.getGUI().showPrompts(prompts);
			} catch(RemoteException e) {
				log.error("Failed to show prompts", e);
				disconnect(true);
				throw new IOException(e);
			}
		}
		return null;
	}
	
	protected void onConnected() {

	}

	@Override
	public void showWarning(String msg) {
		if (service.getGUI() != null) {
			try {
				service.getGUI().notify(msg, GUICallback.NOTIFY_WARNING);
			} catch (RemoteException e) {
				log.error("Failed to show warning", e);
			}
		}
	}

	@Override
	public void showError(String msg) {
		if (service.getGUI() != null) {
			try {
				service.getGUI().notify(msg, GUICallback.NOTIFY_ERROR);
			} catch (RemoteException e) {
				log.error("Failed to show error", e);
			}
		}
	}

	@Override
	protected void onDisconnecting() {
		
		for(ServicePlugin plugin : plugins) {
			try {
				plugin.stop();
			} catch (Throwable e) {
				log.error("Failed to stop plugin " + plugin.getName(), e);
			}
		}
		
	}

}
