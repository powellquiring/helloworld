package badfix.ptools

import java.io.File;

class Constants {
	private static final String PTOOLS_STRING = /C:\powell\groovy\badfixes\ptools/
	private static final File badFixes = new File(/C:\powell\groovy\badfixes/)
	static final File PTOOLS = new File(badFixes, "ptools")
	static final File ptoolsXml = new File(PTOOLS, /ptools.xml/)
	static final File ptoolsCache = new File(PTOOLS, /cache/)
	static final ptoolsTaskListing = new URL("http://capilanobuild.swg.usma.ibm.com:9080/ptools/task/listing")
	private static final pdev = new File(badFixes, /pdev/)

	static final File fixIdUrlFile = new File(pdev, /fixidurl.txt/)

	static final File buildXml = new File(pdev, /build.xml/)
	static final File cwafixup = new File(pdev, /cwafixup/) // createRepositoryDigest output
	static final File copy = new File(pdev, /copy/) // cic.copyRepository output
	static final File repoxml = new File(pdev, /repoxml/) // copy repository.xml file
}