package org.jboss.ide.eclipse.core.test;

import junit.framework.TestCase;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

public class PluginLoadTest extends TestCase {

	private static String jbpkg = "org.jboss.ide.eclipse.";

	private boolean isPluginResolved (String pluginId)
	{
		Bundle bundle = Platform.getBundle(pluginId);
		
		assertNotNull(pluginId + " failed to load.", bundle);
		
		return ((bundle.getState() & Bundle.RESOLVED) > 0);
	}
	
	private void assertPluginsResolved (String[] pluginIds)
	{
		for (int i = 0; i < pluginIds.length; i++) {
			assertTrue (isPluginResolved(pluginIds[i]));
		}
	}
	
	public void testCorePluginsResolved ()
	{
		assertPluginsResolved(new String[] {
			jbpkg+"core", jbpkg+"deployer.core", jbpkg+"deployer.ui",
			jbpkg+"jdt.core", jbpkg+"jdt.j2ee.core", jbpkg+"jdt.j2ee.jsp.core",
			jbpkg+"jdt.j2ee.jsp.ui", jbpkg+"jdt.j2ee.ui", jbpkg+"jdt.j2ee.xml.ui",
			jbpkg+"jdt.test.core", jbpkg+"jdt.test.ui", jbpkg+"jdt.ui",
			jbpkg+"jdt.ws.core", jbpkg+"jdt.ws.ui", jbpkg+"launcher.core",
			jbpkg+"launcher.ui", jbpkg+"packaging.core", jbpkg+"packaging.ui",
			jbpkg+"ui", jbpkg+"xdoclet.assist", jbpkg+"xdoclet.core", jbpkg+"xdoclet.run",
			jbpkg+"xdoclet.ui"
		});
	}
	
	public void testAopPluginsResolved ()
	{
		assertPluginsResolved(new String[] {
			jbpkg+"jdt.aop.core", jbpkg+"jdt.aop.ui"
		});
	}
	
	public void testEjb3PluginsResolved ()
	{
		assertPluginsResolved(new String[] {
			jbpkg+"ejb3.wizards.core", jbpkg+"ejb3.wizards.ui"
		});
	}
	
	public void testHibernatePluginsResolved ()
	{
		assertPluginsResolved(new String[] {
			"org.hibernate.eclipse", "org.hibernate.eclipse.console",
			"org.hibernate.eclipse.help", "org.hibernate.eclipse.mapper"
		});
	}
	
	public void testJbpmPluginsResolved ()
	{
		assertPluginsResolved(new String[] {
			"org.jbpm.core", "org.jbpm.help",
			"org.jbpm.db", "org.jbpm.ui"
		});
	}
}