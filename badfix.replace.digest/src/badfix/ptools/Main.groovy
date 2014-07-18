package badfix.ptools

import groovy.transform.EqualsAndHashCode

import java.awt.image.ImageFilter;
import java.io.File;
import java.util.regex.Matcher;

import org.cyberneko.html.parsers.SAXParser;

import system.FileStatic;
import system.StandardOutToFile;
import system.UriHelper;
import badfix.replace.digest.CwaRepositoryXml;
import static groovyx.gpars.GParsPool.withPool

/**
screen scraping from ptools: http://capilanobuild.swg.usma.ibm.com:9080/ptools/task/listing
in firebug copy the <body> html


**/
class Main {
	// Configuration section
	boolean useOnlyPreviouslyFetchedPtoolsProductFiles = false; // default false  TODO remove this
	
	//////////
	
	static public final Main instance = new Main()
	private Main(){}
	
	private static SAXParser nekoParser =  new org.cyberneko.html.parsers.SAXParser()
	private static XmlParser parser = new XmlParser(nekoParser)

    // global variables initialized in createPtoolsProductList()	
    List<Product>ptoolsProductList = []
    List<Fix> uniqueFixes = []
    Map<String,Product> fix2Product = [:]
    
    static class Product {
        Product(String productName, String productHref, String offeringId) {
            this.productName = productName
            this.productHref = productHref
            this.offeringId = offeringId
        }
        
        String productName
        String productHref
        String offeringId
        List<Fix>fixes
        String toString() {
            return productName
        }
        String dump() {
            StringBuffer fixes = new StringBuffer(1000)
            fixId2Url.each {String fix, String url ->
                fixes << "\n  $fix $url"
            }
            "productName:$productName offeringId:$offeringId\n    productHref:$productHref$fixes"
        }
    }
    
	@EqualsAndHashCode static class FixIdUrl {
		String fixId
		String url
        String toString() {
            return "$fixId | $url"
        }
	}

    @EqualsAndHashCode static class Fix {
        Product product
        String fixId
        List<String> urls
        Fix(Product product, String fixId, List<String> urls) {
            this.product = product
            this.fixId = fixId
            this.urls = urls
        }
        String toString() {
            StringBuffer ret = new StringBuffer()
            ret << fixId
            urls.each{ret << " | $it"}
            return ret.toString()
        }
    }


    // delete me
    Map fix2PtoolHref = [:]
    Map fix2OfferingId = [:]
	

	public main() {
		StandardOutToFile out = new StandardOutToFile("badfix", ".txt")
        UriHelper.getSingleton().initAuthenticator();
        createPtoolsProductList()
        dumpPtoolsProductList()
        copyRepositoryXml()
        File buildXml = generateBuildXml()
        Map<String,Fix> fixUrls = findFixUrlsContainingFixes()
        publishReplacementRepositoryDigests(fixUrls)
        out.closeAndGvim()
        return
        
        Set<String> hrefs = getProductRefs()
        def hrefs2 = getProductRefs2()
        assert hrefs.size() == hrefs2.size()
        hrefs2.each{String href ->
            assert hrefs.contains(href)
        }

        // Set<String> hrefs = getProductRefs()
//        prefetchProductFiles(hrefs)

        makeComposite()
		
		List<FixIdUrl> fixIdUrls = getFixIdUrls(hrefs)
        List<FixIdUrl> fixIdUrls2 = getFixIdUrls2(hrefs)
//		copyRepositoryXml(fixIdUrls)
//		LinkedHashSet<FixIdUrl> containingImfixes = lookForImfixes(fixIdUrls)
				
		makeFixIdUrlFile(fixIdUrls)
		// executePdev(buildXml)

		exaiminePdevResults(fixIdUrls)
		
		out.closeAndGvim()
	}
    
    void publishReplacementRepositoryDigests(Map<String,Fix> fixUrls) {
        println "---- All urls containing fixes"
        fixUrls.each{String url, Fix fix ->
            File replacement = new File(new File(Constants.cwafixup, fix.fixId), fixUrlToId(url))
            println replacement
        }
    }
    
    // populate ptoolsProductList
    void createPtoolsProductList() {
        File ptools = Constants.ptoolsXml
        SAXParser nekoParser =  new org.cyberneko.html.parsers.SAXParser()
        Node root = parser.parse(ptools)
        //      root.BODY.DIV.DIV.TABLE.TBODY.TR.TD.A.each{Node node ->
        root.BODY.DIV.DIV.TABLE.TBODY.TR.each{Node tr ->
            List<Node>tds = tr.TD
            assert tds.size() == 2
            List<Node> links = tds.A
            assert links.size() == 1
            final String productHref = links[0]."@href"
            final String productName = extractProductName(productHref)
            final String offeringId = tds[1].text()
            ptoolsProductList << new Product(productName, productHref, offeringId)
        }
        withPool(10) {
            ptoolsProductList.eachParallel {Product product ->
                fetchPtoolsProductFile(product.productHref, product.productName)
            }
        }

        ptoolsProductList.each {Product product ->
            product.fixes = createFixes(product, product.productName, product.productHref, product.offeringId)
        }
        
        Map<String,Fix> unique = new LinkedHashMap<String,Fix>()
        ptoolsProductList.each {Product product ->
            product.fixes.each{Fix fix ->
                if (!unique.containsKey(fix.fixId)) {
                    unique[fix.fixId] = fix
                    fix2Product[fix.fixId] = product
                }
            }
        }
        unique.values().each{uniqueFixes << it}
    }
    
    // fetch the file from an href.  Put in cache.  Return cached file
    private File fetchPtoolsProductFile(String href) {
        String productName = extractProductName(href)
        return fetchPtoolsProductFile(href, productName)
    }
    
    private File fetchPtoolsProductFile(String href, String productName) {
        File ptoolsProductFile = productFile(productName)
        fetchPtoolsProductFile(href, productName, ptoolsProductFile)
        return ptoolsProductFile
    }
    
    private void fetchPtoolsProductFile(String href, String productName, File ptoolsProductFile) {
        if (ptoolsProductFile.exists()) {
            //println "-${ptoolsProductFile}"
        } else {
            if (useOnlyPreviouslyFetchedPtoolsProductFiles) {
                println "=${ptoolsProductFile} ignoring due to configuraion useOnlyPreviouslyDownloadedPtoolsProductFiles"
                return null
            }
            println "+${ptoolsProductFile}"
            FileStatic.copyUrlToFile(ptoolsTaskListing(href), ptoolsProductFile)
            println "=${ptoolsProductFile}"
        }
    }

    List<Fix> createFixes(Product product, String productName, String productHref, String offeringId) {
        List<Fix> ret = []
        File ptoolsProductFile = fetchPtoolsProductFile(productHref, productName)
        assert ptoolsProductFile
        assert ptoolsProductFile.exists()
        Node root = parser.parse(ptoolsProductFile)
        List<Node> h1 = root.BODY.DIV.H1
        
        if (h1.size() == 0) {   // empty file
            return ret
        }
        
        assert h1.size() == 1
        String offeringIdx = h1[0].text()
        assert offeringId == offeringIdx
        List<FixIdUrl> fixIdUrls = []  // fixes in this product
        root.BODY.DIV.UL.UL.each{Node ul ->
            ret.addAll(extractFixes(product, ul))
        }
        return ret
    }
    
    // extract the fixes from a release
    private List<Fix> extractFixes(Product product, Node ul) {
        List<Fix> fixes = []
        Fix fix
        ul.children().each{Node node ->
            String name = node.name()
            if (!fix) {
                if ((name instanceof String) && name.equals("LI")) {
                    String text = node.text()
                    if (text) {
                        def m = text =~ /Fix ID: (.*)/
                        if (m && m[0].size() == 2) {
                            fix = new Fix(product, m[0][1], new LinkedList<String>())
                        }
                    }
                }
            } else { // foundFix next element is a UL with the first child of LI repository
                List aList = node.LI.A
                assert aList.size() >= 1
                aList.each{Node aNode ->
                    fix.urls << extractUrl(aNode)
                }
                fixes << fix
                fix = null
            }
        }
        return fixes
    }
    
    String extractUrl(Node aNode) {
        String href = aNode.'@href'
        assert href
        def m = href =~ /(.*)\/repository\.xml/
        assert m.size() == 1
        assert m[0].size() == 2
        return m[0][1]
    }
    

    void dumpPtoolsProductList() {
//        ptoolsProductList.each{Product product ->
//            println product
//        }
        
        println "---- Missing products"
        def missingProducts = ptoolsProductList.findAll{Product product -> product.fixes.size() == 0}
        missingProducts.each{Product product ->
            println ptoolsTaskListing(product.productHref)
        }
        println ""
        missingProducts.each{Product product ->
            println urlForProductRepositoryXml(product)
        }
        println ""
        missingProducts.each{Product product ->
            println urlForProductRepositoryConfig(product)
        }
        println ""
        missingProducts.each{Product product ->
            println "del ${productFile(product.productName)}"
        }
        
        Map<String,Map<String,Fix>> fixId2Fixes = new LinkedHashMap<String,Map<String,Fix>>()
        ptoolsProductList.each {Product product ->
            product.fixes.each{Fix fix ->
                Map<String, Fix>productMap = fixId2Fixes.get(fix.fixId, new LinkedHashMap<String,Fix>())
                assert !productMap.containsKey(fix.product.productName)
                productMap[fix.product.productName] = fix
            }
        }
//        println "---- Duplicate Fixes"
//        fixId2Fixes.each{String fixId, Map<String, Fix>productMap ->
//            println fixId
//            productMap.each{String productName, Fix fix ->
//                println "  $productName"
//            }
//        }
        
        println "---- Total uniqueFixes: ${uniqueFixes.size()}"

        Map<String,Map<String,Fix>> fixId2Fix = new LinkedHashMap<String,Fix>()
        ptoolsProductList.each {Product product ->
            product.fixes.each{Fix fix ->
                if (!fixId2Fix.containsKey(fix.fixId)) {
                    fixId2Fix[fix.fixId] = fix
                }
            }
        }
        assert fixId2Fix.size() == uniqueFixes.size()
        def url2 = uniqueFixes.findAll{Fix fix ->
            if (fix.urls.size() == 2) {
                return true
            } else {
                return false
            }
        }
        println "---- Fixes with 2 repos: ${url2.size()}"
        url2.each{Fix fix ->println fix}

        def urlmore = uniqueFixes.findAll{Fix fix ->
            if (fix.urls.size() > 2) {
                return true
            } else {
                return false
            }
        }
        println "---- Fixes with 2 repos: ${urlmore.size()}"
        urlmore.each{Fix fix ->println fix}

        println "---- Verify all fixes have unique repository ids"
        fixId2Fix.values().each{Fix fix ->
            println fix.fixId
            Map<String,String> checkUniqueName = [:]
            fix.urls.each {String url ->
                String id = fixUrlToId(url)
                println "  $id: $url"
                assert !checkUniqueName.containsKey(id)
                checkUniqueName[id] = url
            }
        }
        
        println "---- Verify all fixes have same set of urls"
        ptoolsProductList.each {Product product ->
            product.fixes.each{Fix fix ->
                Fix uniqueFix = fixId2Fix[fix.fixId]
                int i = 0
                fix.urls.each{String url ->
                    String uniqueUrl = uniqueFix.urls[i++]
                    if (url != uniqueUrl) {
                        println fix.fixId
                        println "  $url"
                        println "  $uniqueUrl"
                    }
                }
                
            }
        }

            
    }
    
    String fixUrlToId(String url) {
        // https://public.dhe.ibm.com/software/rationalsdp/v7/im/emptyRepo
        if (url == 'https://public.dhe.ibm.com/software/rationalsdp/v7/im/emptyRepo') {
            return 'httpsv7emptyRepo'
        }
        if (url == 'http://public.dhe.ibm.com/software/rationalsdp/v7/im/emptyRepo') {
            return 'httpv7emptyRepo'
        }
        if (url == 'https://public.dhe.ibm.com/software/rationalsdp/v8/im/emptyRepo') {
            return 'httpsv8emptyRepo'
        }
        if (url == 'http://public.dhe.ibm.com/software/rationalsdp/v8/im/emptyRepo') {
            return 'httpv8emptyRepo'
        }

        // http://delivery04.dhe.ibm.com/sar/CMA/WSA/02lfx/0/8.0.0.0.appclient.PM39432
        // http://delivery04.dhe.ibm.com/sar/CMA/RAA/03z1y/0/repository
        Matcher simple = url =~ 'http://[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/([^/]*)'
        if (simple.matches()) {
            return simple[0][1]
        }
        // http://delivery04.dhe.ibm.com/sdfdl/v2/sar/CM/RA/043nc/0/repository/Xa.4/Xb.jusyLTSp44S03Ud2ji40NCUtQ4_0eR8KBDRwOCo0LObD7BAXG4Wv8AP5nec/Xc.CM/RA/043nc/0/repository//Xd./Xf.Lpr./Xg.7667571/Xi.habanero/XY.habanero/XZ.UZiEd_0gkITeBTxImPeQLl7YziE
        Matcher complex = url =~ 'http://[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/([^/]*)/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*/[^/]*'
        if (complex.matches()) {
            return complex[0][1]
        }
        return "++++"
    }
    
    String urlForProductRepositoryXml(Product product) {
        return "https://www.ibm.com/software/repositorymanager/${product.offeringId}/repository.xml"
    }
    String urlForProductRepositoryConfig(Product product) {
        return "https://www.ibm.com/software/repositorymanager/${product.offeringId}/repository.config"
    }
    
    // copy the repository.xml from the repository into the copy directory.
    // alternative to the pdev build.xml file above
    void copyRepositoryXml() {
        println "--- no repository.xml files in these fix repositories"
        uniqueFixes.each{Fix fix ->
            fix.urls.each {String url ->
                String id = fixUrlToId(url)
                File fixDir = new File(new File(Constants.repoxml, fix.fixId), id)
                fixDir.mkdirs()
                final File repositoryXmlFile = new File(fixDir, "repository.xml")
                if (!repositoryXmlFile.exists()) {
                    boolean urlExists = FileStatic.copyUrlToFile(new URL(url + "/repository.xml"), repositoryXmlFile, /*returnFalseOnNonExistantInputUrl*/ true)
                    if (!urlExists) {
                        // it could be that the repositories urls are entitled and have timed out.  Execute these del commands and re-run to fetch them again
                        Product product = fix2Product[fix.fixId]
                        println "del ${productFile(product.productName)}"
                        println "$fix.fixId https://www.ibm.com/software/repositorymanager/${product.offeringId}/repository.xml"
                        final File repositoryConfigFile = new File(fixDir, "repository.config")
                        if (!repositoryConfigFile.exists()) {
                            assert FileStatic.copyUrlToFile(new URL(url + "/repository.config"), repositoryConfigFile, /*returnFalseOnNonExistantInputUrl*/ true)
                        }
                    }
                }
            }
        }
    }

    File generateBuildXml() {
        File buildXml = Constants.buildXml
        if (buildXml.exists()) {
            assert buildXml.delete()
        }
        def out = buildXml.newOutputStream()
        
        StringBuffer commands = "" << ""
        uniqueFixes.each {Fix fix ->
            fix.urls.each {String url ->
                commands << "         <createRepository fix='${fix.fixId}' id='${fixUrlToId(url)}'  repository='${url}'/>\n"
//                commands << "         <copyRepo fix='${fix.fixId}' id='${fixUrlToId(url)}' repository='${url}'/>\n"
            }
        }
        out << """<?xml version="1.0" encoding="UTF-8"?>
<project name="project" default="default">
    <property name="cwafixup" value="${Constants.cwafixup.getCanonicalPath()}"/>
    <property name="copy" value="${Constants.copy.getCanonicalPath()}"/>
    <macrodef name="createRepository">
        <attribute name="fix" default="NOT SET"/>
        <attribute name="id" default="NOT SET"/>
        <attribute name="repository" default="NOT SET"/>
        <sequential>
             <echo>outputLocation=\${cwafixup}/@{fix}/@{id}</echo>
             <echo>path=@{repository}</echo>
             <cic.createRepositoryDigest>
                        <processedrepository
                            path="@{repository}"
                            splitCompositeRepository="false"
                            ignoreExistingDigests="false"
                            outputLocation="\${cwafixup}/@{fix}/@{id}"
                        />
                    </cic.createRepositoryDigest>
            </sequential>
    </macrodef>
    <macrodef name="copyRepo">
        <attribute name="fix" default="NOT SET"/>
        <attribute name="id" default="NOT SET"/>
        <attribute name="repository" default="NOT SET"/>
        <sequential>
            <cic.copyRepository metadatadestination="\${copy}/@{fix}/@{id}">
                <repository url="@{repository}"/>
            </cic.copyRepository>
        </sequential>
    </macrodef>

    <target name="default" description="description">
${commands}
    </target>
</project>
"""
        out.close()
        return buildXml
    }
    
    LinkedHashMap<String,Fix> findFixUrlsContainingFixes() {
        def parser = new XmlParser()
        LinkedHashMap<String,Fix> containingFixes = []
        LinkedHashSet<String> fixIdVersions = []

        uniqueFixes.each{Fix fix ->
            fix.urls.each {String url ->
                String id = fixUrlToId(url)
                File repositoryXml = new File(new File(new File(Constants.cwafixup, fix.fixId), id), /repository.xml/)
                if (repositoryXml.exists()) {
                    Node repositoryDigest = parser.parse(repositoryXml)
                    int ImfixCountInFix = 0
                    repositoryDigest.depthFirst().each{
                        if (it instanceof Node) {
                            Node n = (Node)it
                            if (n.name() == "fix") {
                                if (ImfixCountInFix != 0) {
                                    println "Multiple fixes in repository: $f"
                                }
                                ImfixCountInFix++
                                containingFixes[url] = fix
                                
    //                          String fixIdVersion = "${n.'@id'}_${n.'@version'}"
    //                          if (fixIdVersions.contains(fixIdVersion)) {
    //                              println "Duplicate $fixIdVersion subsequent found in $f"
    //                          } else {
    //                              fixIdVersions << fixIdVersion
    //                          }
                            }
                        }
                    }
                }
    
            }
        }
        return containingFixes
    }

    /**
     * return the set of ptools project references which are relative path names
     *  <a href="/ptools/task/productLookup?name=ibm%2fTivoli%2fAdministration+Services+UI+in+Jazz+for+Service+Management">Administration Services UI in Jazz for Service Management</a>
     * @return
     */
    private List<String> getProductRefs2() {
        List<String> ret = []
        ptoolsProductList.each {Product product ->
            ret << product.productHref
        }
        return ret
    }

    private Set<String> getProductRefs() {
        File ptools = Constants.ptoolsXml
        SAXParser nekoParser =  new org.cyberneko.html.parsers.SAXParser()
        Node root = parser.parse(ptools)
        Set<String> hrefs = new LinkedHashSet<String>()
        root.BODY.DIV.DIV.TABLE.TBODY.TR.TD.A.each{Node node ->
            hrefs << node.'@href'
        }
        return hrefs
    }
    
    /**
     *
     * @param ul
     * @return list of FixIdUrls
     *
     *
     <ul>
        ... stuff
        <li>Fix ID: FixIdA</li>
        <ul>
          <li>Repository: <a href="RepoFixIdA/repository.xml">RepoFixIdA/repository.xml</a></li>
          ... stuff
        </ul>
        ... stuff
        <li>Fix ID: FixIdB</li>
        <ul>
          <li>Repository: <a href="RepoFixIdB/repository.xml">RepoFixIdB/repository.xml</a></li>
          <li> ... lots of other stuff </li>
        </ul>
     </ul>
     
     returns:
     [(FixIdA,RepoFixIdA/repository.xml),(FixIdB,RepoFixIdB/repository.xml)]
            
     */
    private List <FixIdUrl> extractFixIdUrl(Node ul) {
        List <FixIdUrl> fixIdUrls = []
        boolean foundFix = false
        FixIdUrl fixIdUrl
        ul.children().each{Node node ->
            String name = node.name()
            if (!foundFix) {
                if ((name instanceof String) && name.equals("LI")) {
                    String text = node.text()
                    if (text) {
                        def m = text =~ /Fix ID: (.*)/
                        if (m && m[0].size() == 2) {
                            foundFix = true
                            fixIdUrl = new FixIdUrl()
                            fixIdUrl.fixId = m[0][1]
                        }
                    }
                }
            } else { // foundFix next element is a UL with the first child of LI repository
                List aList = node.LI.A
                assert aList.size() >= 1
                Node a = aList[0]
                String href = a.'@href'
                assert href
                def m = href =~ /(.*)\/repository\.xml/
                assert m.size() == 1
                assert m[0].size() == 2
                fixIdUrl.url = m[0][1]
                fixIdUrls << fixIdUrl
                foundFix = false
            }
        }
        return fixIdUrls
    }
    
	void prefetchProductFiles(Set<String> hrefs) {
		withPool(10) {
			hrefs.eachParallel {String href ->
				fetchPtoolsProductFile(href)
			}
		}
	}
	
	// pdev is generating replace, copy, and repoxml files
	void exaiminePdevResults(List<FixIdUrl> fixIdUrls) {
		println "--- Replacement problems ${fixIdUrls.size()}"
		fixIdUrls.each{FixIdUrl f ->
			File repo = new File(Constants.cwafixup, f.fixId)
			File repositoryXml = new File(repo, "repository.xml")
			File repositoryConfig = new File(repo, "repository.config")
			if (!repo.exists()) {
				println "${repo.getCanonicalPath()} does not exist"
			} else {
				assert repositoryXml.exists()
			}
		}
		
		println "--- Repository.xml copy attempted but no repository.xml file"
		fixIdUrls.each{FixIdUrl f ->
			File repo = new File(Constants.repoxml, f.fixId)
			File repositoryXml = new File(repo, "repository.xml")
			File repositoryConfig = new File(repo, "repository.config")
			if (repo.exists()) {
				if (!repositoryXml.exists()) {
					assert repositoryConfig.exists()
					println(repositoryConfig.getCanonicalPath())
				}
			}
		}
		println "--- cic.copyRepository did not generate a repository.xml file"
		fixIdUrls.each{FixIdUrl f ->
			File repo = new File(Constants.copy, f.fixId)
			File repositoryXml = new File(repo, "repository.xml")
			File repositoryConfig = new File(repo, "repository.config")
			if (repo.exists()) {
				if (!repositoryXml.exists()) {
					println(repo.getCanonicalPath())
					if (repositoryConfig.exists()) {
						println repositoryConfig.getCanonicalPath()
					}
				}
			}
		}

	}
	
	
	void makeFixIdUrlFile(List<FixIdUrl> fixIdUrls) {
		FileStatic.deleteAndCreateFile(Constants.fixIdUrlFile) {out ->
			fixIdUrls.each {FixIdUrl f ->
				out << "${f.fixId} ${f.url}\n"
			}
		}
	}
	
	void makeComposite() {
		File repositoryConfig = new File(Constants.cwafixup, "repository.config")
		repositoryConfig.delete()
		def out = repositoryConfig.newOutputStream()
		out << "LayoutPolicy=Composite\n"
		out << "LayoutPolicyVersion=0.0.0.1\n"
		Constants.cwafixup.eachDir {File f ->
			if (f.isDirectory()) {
				out << "repository.url.${f.getName()}=./${f.getName()}\n"
			}
		}
		out.close()
	}
	
	// Get all of the fix urls for the ptools product references
	List<FixIdUrl> getFixIdUrls(Set<String> hrefs) {
		List<FixIdUrl> ret = []
		Map<String,FixIdUrl>fixToUrl = [:]
		// find the list for each href
		int hrefCountDown = hrefs.size()
		hrefs.each{String href ->
			println hrefCountDown--
			File ptoolsProductFile = fetchPtoolsProductFile(href)
			if (ptoolsProductFile == null) {
				assert useOnlyPreviouslyFetchedPtoolsProductFiles
				return
			}
			Node root = parser.parse(ptoolsProductFile)
            List<Node> h1 = root.BODY.DIV.H1
            assert h1.size() == 1
            String offeringId = h1[0].text()
			List<FixIdUrl> fixIdUrls = []  // fixes in this href
			root.BODY.DIV.UL.UL.each{Node ul ->
				List <FixIdUrl> fixIdUrlInUl = extractFixIdUrl(ul)
				fixIdUrls.addAll(fixIdUrlInUl)
                fixIdUrlInUl.each{FixIdUrl f ->
                    fix2OfferingId[f.fixId] = offeringId
                }
			}
			
			// only add the unique fixes
			fixIdUrls.each{FixIdUrl f ->
				if (fixToUrl.containsKey(f.fixId)) {
					if (f.url != fixToUrl[f.fixId].url) {
						// println "-- duplicate fix ID ${f.fixId} ignored ${f.url} - using ${fixToUrl[f.fixId].url}"
//						if (!urlSameExceptGuid(f.url, fixToUrl[f.fixId].url)) {
//							println "-- duplicate fix ID ${f.fixId} ignored ${f.url} - using ${fixToUrl[f.fixId].url}"
//						}
					}
				} else {
					fixToUrl[f.fixId] = f
					fix2PtoolHref[f.fixId] = ptoolsProductFile
					// println "++$f"
					ret << f
				}
				
			}
		}
		return ret
	}
    // Get all of the fix urls for the ptools product references
    List<FixIdUrl> getFixIdUrls2(Set<String> hrefs) {
        List<FixIdUrl> ret = []
        ptoolsProductList.each {Product product ->
            product.fixId2Url.each{String fixId, String url ->
                
            }
        }
        
        
        Map<String,FixIdUrl>fixToUrl = [:]
        // find the list for each href
        int hrefCountDown = hrefs.size()
        hrefs.each{String href ->
            println hrefCountDown--
            File ptoolsProductFile = fetchPtoolsProductFile(href)
            if (ptoolsProductFile == null) {
                assert useOnlyPreviouslyFetchedPtoolsProductFiles
                return
            }
            Node root = parser.parse(ptoolsProductFile)
            List<Node> h1 = root.BODY.DIV.H1
            assert h1.size() == 1
            String offeringId = h1[0].text()
            List<FixIdUrl> fixIdUrls = []  // fixes in this href
            root.BODY.DIV.UL.UL.each{Node ul ->
                List <FixIdUrl> fixIdUrlInUl = extractFixIdUrl(ul)
                fixIdUrls.addAll(fixIdUrlInUl)
                fixIdUrlInUl.each{FixIdUrl f ->
                    fix2OfferingId[f.fixId] = offeringId
                }
            }
            
            // only add the unique fixes
            fixIdUrls.each{FixIdUrl f ->
                if (fixToUrl.containsKey(f.fixId)) {
                    if (f.url != fixToUrl[f.fixId].url) {
                        // println "-- duplicate fix ID ${f.fixId} ignored ${f.url} - using ${fixToUrl[f.fixId].url}"
//                      if (!urlSameExceptGuid(f.url, fixToUrl[f.fixId].url)) {
//                          println "-- duplicate fix ID ${f.fixId} ignored ${f.url} - using ${fixToUrl[f.fixId].url}"
//                      }
                    }
                } else {
                    fixToUrl[f.fixId] = f
                    fix2PtoolHref[f.fixId] = ptoolsProductFile
                    // println "++$f"
                    ret << f
                }
                
            }
        }
        return ret
    }
    

	boolean urlSameExceptGuid(String newUrl, String oldUrl) {
		String[] newParts = newUrl.split('/')
		String[] oldParts = oldUrl.split('/')
		if (newParts.length != oldParts.length) return false
		int differentParts = 0
		for(int i = 0; i < newParts.length; ++i) {
			if (newParts[i] != oldParts[i]) differentParts++
		}
		if (differentParts > 1) return false
		return true
	}
	
    private URL ptoolsTaskListing(String productHref) {
        return new URL(Constants.ptoolsTaskListing, productHref)
    }
	
	private String extractProductName(String href) {
		return href.split('=')[1]
	}
    
    private File productFile(String productName) {
        return new File(Constants.ptoolsCache, productName)
    }

	public static main(String[] args) {
		instance.main()
	}
}
