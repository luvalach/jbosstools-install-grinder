import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.taskdefs.Get;
import groovy.util.AntBuilder;
import java.text.SimpleDateFormat;

void usage() {
	println """
-------------------------------------------------------------
Script to test installation
usage: groovy.sh testInstall.groovy <eclipse_home> <file_containing_list_of_sites|repository_url|CHECK_FOR_UPDATES|MARKETPLACE=<label>>(;)*
	<eclipse_home>: an eclipse installation will be performed on
	<file_containing_list_of_sites> a file containing a list of p2-friendly URLs of repositories separated by spaces or line breaks
	<repository_url>: URL of a p2 repo to install from
	CHECK_FOR_UPDATES: will trigger the check for updates
	MARKETPLACE=<label>: will install <label> from marketplace
--------------------------------------------------------------
usage for installing selected units: groovy.sh -DIUs=iu1,iu2,... testInstall.groovy <eclipse_home> <repository_url>
--------------------------------------------------------------
Commandline flags:
	-DIUs=iu1,iu2,...	Select individual units
	-DADDSITE=http://updatesite1/,http://updatesite2/,http://updatesite3/,...	Additional sites to make available
	-DINSTALLATION_TIMEOUT_IN_MINUTES=30	How long to wait for discovery/installation/long operations
	-DSWTBOT_UPDATE_SITE=http://download.jboss.org/jbosstools/updates/requirements/swtbot/2.1.1.201307101628/	Where to get SWTBot from
	-DJVM=/qa/tools/opt/jdk1.7.0_last/bin/java	Which JVM to use
		-DdebugPort=8000	Enable a debugPort to connect to a remote debugger
	-DEXCLUDE_CONNECTORS=c1,c2,...
	-DINCLUDE_CONNECTORS=c1,c2,...
	-DDISCOVERY_SITE_PROPERTY=jboss.discovery.site.url (default)
--------------------------------------------------------------

	"""
}


/* Takes a repo or a directory.xml URL single parameter
 * @param scenario defines what to install.
 * For Central, scenario can be of form
 * <directory.xml-url>=-<connectorToExclude>,-<connectorToExclue>
 * The = sign and parts after it are optional paremters:
 *	-<connectorToExclude> excludes a connector from installation, based on its id
 */
void runInstallTest(String scenario, File eclipseHome, String product) {
	String[] details = scenario.split("=")
	String url = details[0];

	// TODO: possible workaround for oomph problems? Start Eclipse and shut down after 30 seconds, to enforce the -clean flag
	//initializeEclipse(eclipseHome);

	if (url.endsWith(".xml")) {
		def connectorsToExclude= []
		if (details.length > 1) {
			details[1].split(",").each { param ->
				if (param.charAt(0) == '-') {
					connectorsToExclude.add(param.substring(1))
				}
			}
		}
		installFromCentral(url, eclipseHome, product, connectorsToExclude);
	} else if (url.endsWith(".zip")) {
		installZipRepo(url, eclipseHome, product);
	} else if (scenario.equals("CHECK_FOR_UPDATES")) {
		checkForUpdates(eclipseHome, product);
	} else if (scenario.startsWith("MARKETPLACE")) {
		if (details.length < 2) {
			usage();
		} else {
			installFromMarketplace(details[1], eclipseHome, product);
		}
	} else {
		installRepo(url, eclipseHome, product);
	}
}

// TODO: possible workaround for oomph problems? Start Eclipse and shut down after 30 seconds, to enforce the -clean flag
void initializeEclipse(File eclipseHome){
	println(" === Launching: eclipse -clean === ");
	String cmdLine = eclipseHome.toString() + File.separator + "eclipse -clean -consolelog -debug -console";
	Process eclipseProc = cmdLine.execute();
	eclipseProc.waitForOrKill(eclipseProc,30000);
	println(" === Launched: eclipse -clean === ");
}

void installZipRepo(String repoUrl, File eclipseHome, String productName){

	Collection<String> additionalVMArgs = [];

	if(new File(repoUrl).isFile()){
		//local file, no need to download
		additionalVMArgs += "-DZIP=" + repoUrl;
	}else{
		// wget zip file
		println("DOWNLOAD FIRST: " + repoUrl);

		String zipName = repoUrl.substring(repoUrl.lastIndexOf("/")+1);
		File zip = new File("./" + zipName);
		new AntBuilder().get(src: repoUrl, dest: zip.getAbsolutePath());
		additionalVMArgs += "-DZIP=" + zip.getAbsolutePath();
	}

	// run install zip test
	println("Installing content from " + repoUrl);
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallZipTest");

}

//site = comma separated list of sites to be added
void addSite(String site, File eclipseHome, String productName){
	Collection<String> additionalVMArgs = [];
	additionalVMArgs += "-DADDSITE=" + site;
	println("Add Software sites (no installation):" + site);
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.AddSiteTest");
}

//Takes repo URL as single parameter
void installRepo(String repoUrl, File eclipseHome, String productName) {
	println("Installing content from " + repoUrl);
	Collection<String> additionalVMArgs = [];
	additionalVMArgs += "-DUPDATE_SITE=" + repoUrl;
	String ius = System.properties['IUs'];
	if(ius != null){
		ius=ius.replaceAll("\"","");
		println("Units to install:" + ius);
		additionalVMArgs += " -DIUs=\"" + ius + "\"";
	}
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallTest");
}

void runSWTBotInstallRoutine(File eclipseHome, String productName, Collection<String> additionalVMArgs, String testClassName) {
	String report = "TEST-install-" + new SimpleDateFormat("yyyyMMddh-hmm").format(new Date()) + ".xml";

	// Invoke tests
	Java proc = new org.apache.tools.ant.taskdefs.Java();
	proc.setFork(true);
	proc.setDir(eclipseHome);

	Collection<String> vmArgs = [];
	vmArgs.addAll(additionalVMArgs);
	String osName = System.properties['os.name'].toLowerCase();
	if(osName.contains("mac")){
		vmArgs += "-XstartOnFirstThread";
	}
	vmArgs += "-Dusage_reporting_enabled=false";
	vmArgs += "-Doomph.setup.questionnaire.skip=true";
	vmArgs += "-Doomph.setup.skip=true";
	vmArgs += "-Dorg.eclipse.recommenders.stacktraces.rcp.skipReports=true"
	if (System.getProperty("INSTALLATION_TIMEOUT_IN_MINUTES") != null) {
		vmArgs += "-DINSTALLATION_TIMEOUT_IN_MINUTES=" + System.getProperty("INSTALLATION_TIMEOUT_IN_MINUTES");
	}
	vmArgs += "-Xms256M"; vmArgs += "-Xmx768M"; vmArgs += "-XX:MaxPermSize=512M";

	String debugPort = System.getProperty("debugPort");
	if (debugPort != null) {
		vmArgs += "-agentlib:jdwp=transport=dt_socket,address=" + debugPort + ",server=y,suspend=y";
	}
	println "vmArgs=" + vmArgs.join(" ")

	proc.setJvmargs(vmArgs.join(" "));

	proc.setJar(new File(eclipseHome, "plugins").listFiles().find {it.getName().startsWith("org.eclipse.equinox.launcher_") && it.getName().endsWith(".jar")} );
	Collection<String> args = [];
	args += "-application"; args += "org.eclipse.swtbot.eclipse.junit.headless.swtbottestapplication";
	args += "-testApplication"; args += "org.eclipse.ui.ide.workbench";
	args += "-product"; args += productName;
	args += "-data"; args += "workspace";
	args += "formatter=org.apache.tools.ant.taskdefs.optional.junit.XMLJUnitResultFormatter," + report;
	args += "formatter=org.apache.tools.ant.taskdefs.optional.junit.PlainJUnitResultFormatter";
	args += "-testPluginName"; args += "org.jboss.tools.tests.installation";
	args += "-className"; args += testClassName;
	args += "-consoleLog";
	args += "-clean";
	args += "-showLocation";
	args += "-debug";
	proc.setArgs(args.join(" "));
	proc.init();
	println(" === Launching: swtbottestapplication === ");
	int returnCode = proc.executeJava();
	if (returnCode != 0) {
		println("An error occured; could be due to incorrect runtime environment configuration.");
		System.exit(1);
	}
	println(" === Launched: swtbottestapplication === ");

	File report_file = new File(eclipseHome.getAbsolutePath() + "/" + report);

	report_file.eachLine { line ->
		if (line.contains("failures")) {
			if (line.contains("errors=\"0\" failures=\"0\"")) {
				println("Install SUCCESS. Read " + report + " for more details.");
				return;
			} else {
				println("Failed to install. Read " + report + " for details; see also screenshots/ folder.");
				System.exit(1);
			}
		}
	}
}

// Takes a Central directory.xml URL single parameter, and assumes the accomanying update site is one path segment up:
// for http://download.jboss.org/jbosstools/discovery/development/4.1.0.Alpha2/jbosstools-directory.xml
// use http://download.jboss.org/jbosstools/discovery/development/4.1.0.Alpha2/
// for http://download.jboss.org/jbosstools/discovery/nightly/core/trunk/jbosstools-directory.xml
// use http://download.jboss.org/jbosstools/discovery/nightly/core/trunk/
void installFromCentral(String discoveryDirectoryUrl, File eclipseHome, String productName, Collection<String> connectorsToExclude) {
	println("Installing content from " + discoveryDirectoryUrl);
	String discoverySiteUrl=discoveryDirectoryUrl.substring(0,discoveryDirectoryUrl.lastIndexOf("/")+1);
	Collection<String >additionalVMArgs = [];
	additionalVMArgs.add("-Djboss.discovery.directory.url=" + discoveryDirectoryUrl);
	if (System.properties['DISCOVERY_SITE_PROPERTY'] != null) {
	additionalVMArgs.add("-D" + System.properties['DISCOVERY_SITE_PROPERTY'] + "=" + discoverySiteUrl)	
	} else {
	additionalVMArgs.add("-Djboss.discovery.site.url=" + discoverySiteUrl);
	}
	if (System.properties['EXCLUDE_CONNECTORS'] != null) {
		additionalVMArgs.add("-Dorg.jboss.tools.tests.installFromCentral.excludeConnectors=" + System.properties['EXCLUDE_CONNECTORS'])	
	} else if (!connectorsToExclude.isEmpty()) {
	// Preserve compatibility with previous commit JBIDE-17023
	additionalVMArgs.add("-Dorg.jboss.tools.tests.installFromCentral.excludeConnectors=" + connectorsToExclude.join(","))
	}
	if (System.properties['INCLUDE_CONNECTORS'] != null) {
		additionalVMArgs.add("-Dorg.jboss.tools.tests.installFromCentral.includeConnectors=" + System.properties['INCLUDE_CONNECTORS'])	
	}

	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallFromCentralTest");
}

// Install from marketplace
void installFromMarketplace(String marketplaceLabel, File eclipseHome, String productName) {
	println("Installing '" + marketplaceLabel + "' from marketplace");
	Collection<String> additionalVMArgs = [];
	additionalVMArgs.add("-DMARKETPLACE_LABEL=\"" + marketplaceLabel + "\"");
	runSWTBotInstallRoutine(eclipseHome, productName, additionalVMArgs, "org.jboss.tools.tests.installation.InstallFromMarketplaceTest");
}

// Check for updates
void checkForUpdates(File eclipseHome, String productName) {
	println("Check for updates");
	runSWTBotInstallRoutine(eclipseHome, productName, [], "org.jboss.tools.tests.installation.CheckForUpdatesTest");
}

//// Launcher script
if (args.length < 2) {
	usage();
	System.exit(2);
}

File eclipseHome = new File(args[0]);

if (!eclipseHome.isDirectory()) {
	usage();
	System.exit(2);
}

if(System.properties['IUs'] && (args.length != 2)){
	println("Installing selected/filtered Units is supported only from one repository_url!");
	usage();
	System.exit(2);
}

println "Preparing tests, installing framework";

String companionRepoLocation = System.getProperty("companionRepo");
if (companionRepoLocation == null) {
	companionRepoLocation = getClass().protectionDomain.codeSource.location.path;
	companionRepoLocation = companionRepoLocation[0..companionRepoLocation.lastIndexOf('/')];
	companionRepoLocation += "repository";
	println companionRepoLocation;
}
// Install test framework
Java proc = new org.apache.tools.ant.taskdefs.Java();

// JBIDE-16304: support option to pass in alternate JVM path, eg., /qa/tools/opt/jdk1.7.0_last/bin/java or /qa/tools/opt/jdk1.6.0_last/bin/java
// If not set fall back to default, which is proc.setJvm("java") so whatever's on the current PATH will be used (probably JDK 6)
// In Jenkins job config, set in Groovy script file > Advanced... > Properties > 
//	JVM=/qa/tools/opt/jdk1.7.0_last/bin/java
String JVM = System.properties['JVM'];
if(JVM != null){
		JVM=JVM.replaceAll("\"","");
		proc.setJvm(JVM);
}

proc.setDir(eclipseHome);
proc.setFork(true);
proc.setJar(new File(eclipseHome, "plugins").listFiles().find({it.getName().startsWith("org.eclipse.equinox.launcher_") && it.getName().endsWith(".jar")}).getAbsoluteFile());
// JBIDE-16269: parameterize the URL from which SWTBot is installed, eg., with 
// In Jenkins job config, set in Groovy script file > Advanced... > Properties > 
//	SWTBOT_UPDATE_SITE=http://download.jboss.org/jbosstools/updates/requirements/swtbot/2.1.1.201307101628/
String SWTBOT_UPDATE_SITE = System.properties['SWTBOT_UPDATE_SITE'];
if(SWTBOT_UPDATE_SITE != null){
		SWTBOT_UPDATE_SITE=SWTBOT_UPDATE_SITE.replaceAll("\"","");
}
else
{
		SWTBOT_UPDATE_SITE="http://download.eclipse.org/technology/swtbot/releases/latest/";
}
println("Install SWTBot from: " + SWTBOT_UPDATE_SITE);
proc.setArgs("-application org.eclipse.equinox.p2.director " +
		"-repository " + SWTBOT_UPDATE_SITE + "," +
		"file:///" + companionRepoLocation + " " +
		"-installIU org.jboss.tools.tests.installation " +
		"-installIU org.eclipse.swtbot.eclipse.test.junit.feature.group " +
		"-consolelog");
proc.init();
int returnCode = proc.executeJava();
if (returnCode != 0) {
	System.exit(3);
}

File iniFile;
String osName = System.properties['os.name'].toLowerCase();
if(osName.contains("mac")){
	//Mac OSX
	iniFile = new File(eclipseHome.getAbsolutePath() + "/Eclipse.app/Contents/MacOS").listFiles().find({it.getName().endsWith(".ini")});
	if(iniFile == null){
	iniFile = new File(eclipseHome.getAbsolutePath() + "/jbdevstudio.app/Contents/MacOS").listFiles().find({it.getName().endsWith(".ini")});
	}
	if(iniFile == null){
	iniFile = new File(eclipseHome.getAbsolutePath() + "/JBoss Developer Studio.app/Contents/MacOS").listFiles().find({it.getName().endsWith(".ini")});
	}
}else{
	iniFile = eclipseHome.listFiles().find({it.getName().endsWith(".ini")});
}
iniLines = iniFile.readLines();
targetIndex = iniLines.findIndexOf {line -> line.startsWith("-product") };
String productName = iniLines[targetIndex + 1];
println ("Product is: " + productName);

// Add software sites (no installation)
String newSite = System.properties['ADDSITE'];
if(newSite != null)
	addSite(newSite, eclipseHome, productName);

// End of 'Add software sites (no installatio)

def sites = [];
args[1..-1].each {
	// Allow semicolon-separated lists as well
	sites.addAll it.split(";")
}
sites.each {
	if (new File(it).isFile()) {

		if(it.endsWith(".zip")){
			installZipRepo(it, eclipseHome, productName);
		}else{
			new File(it).eachLine({ line ->
				runInstallTest(line, eclipseHome, productName);
			});
		}
	} else {
		runInstallTest(it, eclipseHome, productName);
	}
}
System.exit(0)
