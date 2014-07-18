package badfix.replace.digest

import system.TeeOutputStream

class CwaRepositoryXml {
	static public final CwaRepositoryXml instance = new CwaRepositoryXml()
	private CwaRepositoryXml(){}
	
	class FixInRepo {
		Node repo
		Node fix
		FixInRepo(Node repo, Node fix) {
			this.repo = repo
			this.fix = fix
		}
		String pathToFixFile() {return "${repo.'@location'}/Fixes/${fix.'@id'}_${fix.'@version'}.fix"}
		
	}
	void main() {
		XmlParser parser = new XmlParser()
		Node root = parser.parse(Constants.repositoryXml)
//		Node root = parser.parse(Constants.repositorySmallXml)
		assert(root.name() == "repositoryDigest")
		// recommended(root)
		allFixes(root)
	}
	
	void allFixes(Node root) {
//		root.depthFirst().findAll{Node node -> node.name() == "fix"}.each{Node node ->
		root.depthFirst().each {
			if (it instanceof Node) {
				Node node = (Node) it
				if (node.name() != "fix") return
				assert node.parent().name() == "repository"
				assert node.parent().parent().name() == "repository"
				assert node.parent().parent().parent().name() == "repositoryDigest"
			} else {
			}
			
		}
	}
	
	void recommended(Node root) {
		List<Node>repos = root.repository.repository
		List<String> cmRepos
		List<FixInRepo> badFixs = []
		repos.each {Node repo ->
			boolean containsFix = false
			repo.fix.each { Node fix ->
				def fixProperties = propertyNodeToPropertyMap(fix)
				FixInRepo fir = new FixInRepo(repo, fix)
				String fixUrlString = fir.pathToFixFile()
				URL fixUrl = new URL(fixUrlString)
				try {
					InputStream realFixStream = fixUrl.openStream()
					Node realFix = parser.parse(realFixStream)
					println fixUrlString
					def realFixProperties = propertyNodeToPropertyMap(realFix)
					println "digest:${stringOrNull(fixProperties['recommended'])} real:${stringOrNull(realFixProperties['recommended'])}"
					realFixStream.close()
				} catch (Exception e) {
					e.printStackTrace()
					badFixs << fir
				}
			}
		}
		println "----bad fixes:"
		badFixs.each{FixInRepo fir ->
			printFixInRepo(fir)
		}

	}
		
	String stringOrNull(String s) {
		if (s == null) return "null"
		return s
	}
	
	Map propertyNodeToPropertyMap(Node propertyNode) { // this node has children <property> nodes
		def returnMap = [:]
		propertyNode.each{Node property -> returnMap[property.'@name'] = property.'@value'}
		return returnMap
	}
	
	void printFixInRepo(FixInRepo fir) {
		println("${fir.fix.'@id'}_${fir.fix.'@version'} ${fir.pathToFixFile()}")
	}
	
	
	public static main(String[] args) {
		instance.main()
//		File outFile = File.createTempFile("cwarepositoryxml", ".txt")
//
//		OutputStream tee = new TeeOutputStream((OutputStream)System.out, outFile.newOutputStream())
//		PrintStream oldSystemOut = System.out
//		System.out = new PrintStream(tee)
//		instance.main()
//		tee.close()
//		["gvim", "${outFile.getCanonicalPath()}"].execute()
	}
}