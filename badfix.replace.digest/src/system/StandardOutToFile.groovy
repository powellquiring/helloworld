package system

class StandardOutToFile {
	final PrintStream oldSystemOut
	final OutputStream tee
	final File outFile
	StandardOutToFile(String tempFileId, String tempFileExtension) {
		outFile = File.createTempFile(tempFileId, tempFileExtension)
		tee = new TeeOutputStream((OutputStream)System.out, outFile.newOutputStream())
		oldSystemOut = System.out
		System.out = new PrintStream(tee)
	}
	
	void closeAndGvim() {
		tee.close()
		["gvim", "${outFile.getCanonicalPath()}"].execute()
		
	}

}
