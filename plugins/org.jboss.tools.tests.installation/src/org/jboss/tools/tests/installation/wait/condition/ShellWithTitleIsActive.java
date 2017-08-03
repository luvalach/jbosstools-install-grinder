package org.jboss.tools.tests.installation.wait.condition;

import org.eclipse.swtbot.swt.finder.SWTBot;
import org.eclipse.swtbot.swt.finder.waits.ICondition;

public class ShellWithTitleIsActive implements ICondition {

	protected SWTBot bot;
	protected String regexp;
	
	public ShellWithTitleIsActive(String regexp) {
		this.regexp = regexp;
	}
	
	@Override
	public boolean test() throws Exception {
		return bot.activeShell().getText().matches(regexp);
	}

	@Override
	public void init(SWTBot bot) {
		this.bot = bot;
	}

	@Override
	public String getFailureMessage() {
		return "Shell with title matching '" + regexp + "' not found.";
	}
}
