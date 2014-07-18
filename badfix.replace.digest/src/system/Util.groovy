package system

class Util {
    def env = [:]
    def homeDir

    private static util
    static def getUtil() {
        if (util == null) {
            util = new Util()
            util.initialize()
        }
        return util
    }

    def initialize() {
        def getEnvCommand='cmd /c set'
        def proc = getEnvCommand.execute()
        // get the windows environment variables
        proc.in.readLines().each{
            def leftRight = it.split('=')
            if (leftRight.size() >= 2) {env[leftRight[0]] = leftRight[1]}
        }
        homeDir = new File(env['HOMEDRIVE'] + env['HOMEPATH'])
        assert homeDir.exists()
    }
}


