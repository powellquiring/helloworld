package badfix.replace.digest
class Constants {
	static final String entitledRepositoryXml = 'https://www-912.ibm.com/software/repositorymanager/entitled/repository.xml'
	static final String FILES = /C:\powell\groovy\badfixes\files/
	static final File repositoryXml = new File(new File(FILES), /repository.xml/)
	static final File repositorySmallXml = new File(new File(FILES), /cwarepositorysmall.xml/)
	static final File newRepositoryXml = new File(new File(FILES), /newcwarepository.xml/)
}