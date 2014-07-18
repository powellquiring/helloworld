package system

class UriHelper extends Authenticator {
    private util
    private homeInfoFile
    private homeInfoContents
    private static final homeInfoFileName = 'info.xml'
    private boolean debug = true
    
    static UriHelper credentials
    static UriHelper getSingleton() {
        if (credentials == null) {
            credentials = new UriHelper()
            credentials.initialize()
        }
        return credentials
    }

    void initialize() {
        util = Util.getUtil()
        homeInfoFile = new File(util.homeDir, homeInfoFileName)
        assert homeInfoFile.exists()
        homeInfoContents = new XmlParser().parse(homeInfoFile)
    }

    def getU(def hostString) {
        if (debug) println homeInfoContents
        def host = homeInfoContents.host.find{it.'@name' == hostString}
        if (host == null) {
            host = homeInfoContents.host.find{it.'@name' == '*'}
        }
        return host.'@u'
    }

    def getP(def hostString) {
        def host = homeInfoContents.host.find{it.'@name' == hostString}
        if (host == null) {
            host = homeInfoContents.host.find{it.'@name' == '*'}
        }
        return host.'@p'
    }

    // initialize an authentictor
    void initAuthenticator() {
        Authenticator.setDefault(this)
    }

    protected  PasswordAuthentication getPasswordAuthentication() {
        if (debug) {
            println 'getting authentication: ' + getRequestingHost()
            println '1'
            println getU(getRequestingHost())
            println '2'
            println getP(getRequestingHost())
            println '3'
            println getU(getRequestingHost()) + ":" + getP(getRequestingHost())
        }
        PasswordAuthentication pa =  new PasswordAuthentication(getU(getRequestingHost()), getP(getRequestingHost()).toCharArray())
        if (debug) {
            println pa.getPassword()
            println "X" + pa.getUserName() + "X"
            println getRequestingHost()
            println getRequestingPort()
            println getRequestingPrompt()
            println getRequestingProtocol()
            println getRequestingScheme()
            println getRequestingSite()
            println getRequestingURL()
            println getRequestorType()
        }
        return pa
    }
}
