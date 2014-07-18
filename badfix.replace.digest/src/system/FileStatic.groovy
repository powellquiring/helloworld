package system

import java.io.File;
import java.net.URL;

class FileStatic {
	static boolean copyUrlToFile(URL inputUrl, File outputFile, boolean returnFalseOnNonExistantInputUrl = false) {
		File tmpFile = new File(outputFile.getParentFile(), "." + outputFile.getName())
		if (tmpFile.exists()) {
			tmpFile.delete()
		}
		BufferedOutputStream tmpStream = tmpFile.newOutputStream()
		final InputStream inputStream
		try {
			inputStream = inputUrl.openStream()
		} catch (Exception e) {
			if (returnFalseOnNonExistantInputUrl) {
				return false
			} else {
				throw e
			}
		}
		assert inputStream
		tmpStream << inputStream
		inputStream.close()
		tmpStream.close()
		assert(tmpFile.renameTo(outputFile))
		return true
	}
	
	static def deleteAndCreateFile(File file, Closure closure) {
		def out = file.newOutputStream()
		file.delete()
		closure(out)
		out.close()
	}
}

