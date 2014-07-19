// exp branch fix
package badfix.replace.digest

import system.FileStatic;
import system.UriHelper;

class Generate {
	static final Generate instance = new Generate()
	private Generate(){}
	public static void main(String[] args){
		instance.doit()
	}
	
	public void doit() {
		copyRepositoryXml()
	}
	private void copyRepositoryXml() {
		println "+${Constants.newRepositoryXml}"
		UriHelper.getSingleton().initAuthenticator();
		FileStatic.copyUrlToFile(new URL(Constants.entitledRepositoryXml), Constants.newRepositoryXml)
	}
	
}
/*

files/
    20140605_0739.xml: list of bad fixes
    cwarepository.xml: all current metadata from the entitled repo, fetched using firefox, used to generate the bad/ contents below
    bad/id_version.xml: current xml for bad fixes
    reposcurrent/id_version/: repository that contains the fix metadata copied using pdev from cwa
    good/FIX.xml: corrected xml 
    reposreplace/composite/: composite of all the FIXes
    reposreplace/FIX/repository.xml: generated from good/FIX.xml with the repo urls from ppa.xml
	
Testing

verify the only difference between the bad/id_version.xml and the good/FIX.xml is: 
o additional <property name='applicable.offerings'..> property 
o additional recommended flag


listavailable -Dcic.ignore.bad.fixes=none output before compared to listavailable after (no system property)
This will verify the recommended flag and the applicable.offerings property are correct.
Verify that there are no complaints in the log

 */

