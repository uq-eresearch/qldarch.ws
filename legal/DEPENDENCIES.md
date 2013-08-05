Legal Status and Licensing of Dependencies
==========================================

ALL third-party libraries, tools or other code MUST be listed here.

Raw original tarballs, zipfiles, or other downloads SHOULD be placed in dependencies/.

The tmp/ directory is in .gitignore, so makes a useful place to expand any archives to
allow extracting specific files into lib/, lib/build, or lib/test.

The five minimum fields required for any dependency are given in the template below:

Project:
URL:
License:
FILES:
JARS:

Project: Apache Commons Codec
URL: http://commons.apache.org/proper/commons-codec/
License: Apache 2.0
FILES: commons-codec-1.4-bin.tar.gz
JARS: commons-codec-1.4.jar
NOTE: This project is very out of date, but a dependency for sesame.

Project: Apache Commons HttpClient
URL: http://hc.apache.org/httpclient-3.x/
License: Apache 2.0
FILES: commons-httpclient-3.1.tar.gz
JARS: commons-httpclient-3.1.jar
NOTE: This project is EOL but a dependency for sesame.

Project: Junit
URL: http://junit.org/
License: CPL v1.0
FILES: junit-4.10.jar
JARS: test/junit-4.10.jar

Project: Logback
URL: http://logback.qos.ch/
License: Dual EPL v1.0/LGPL v2.1 or later
FILES: logback-1.0.11.tar.gz
JARS: logback-classic-1.0.11.jar logback-core-1.0.11.jar

Project: Simple Logging Facade for Java
URL: http://www.slf4j.org/index.html
License: MIT
FILES: slf4j-1.7.5.tar.gz
JARS: jcl-over-slf4j-1.7.5.jar slf4j-api-1.7.5.jar

Project: Sesame
URL: http://www.openrdf.org/
License: BSD 3-clause
FILES: Compiled from source
JARS: openrdf-sesame-2.7-SNAPSHOT-onejar.jar

Project: Google Guava
URL: http://code.google.com/p/guava-libraries/
License: Apache 2.0
FILES: guava-14.0.1-javadoc.jar guava-14.0.1.jar
JARS: guava-14.0.1.jar

Project: ASM
URL: http://asm.ow2.org/
License: 3-clause BSD
FILES: asm-3.1.zip
JARS: asm-3.1.jar

Project: Jersey RESTful Web Services in Java
URL: https://jersey.java.net/
License: Apache 2.0
FILES: 
JARS: jersey-client-1.13.jar jersey-core-1.13.jar jersey-server-1.13.jar jersey-servlet-1.13.jar

Project: Jetty Servlet Engine
URL: http://www.eclipse.org/jetty/
License: Dual Apache 2.0, EPL 1.0
FILES:
JARS: jetty-all-8.1.5.v20120716.jar

Project: JSR 311
URL: https://jsr311.java.net/
License: CDDL
FILES:
JARS: jsr311-api-1.1.1.jar

Project: Ant2Ide - IDE project file generation from Ant build.xml.
URL: http://gleamynode.net/articles/2234/
LICENSE: unknown
FILES: dependencies/ant2ide.jar
JARS: ant2ide.jar

Project: Apache Shiro Security Framework
URL: http://shiro.apache.org/index.html
LICENSE: Apache 2.0
FILES: dependencies/shiro-all-1.2.2.jar
JARS: shiro-all-1.2.2.jar shiro-tools-hasher-1.2.2-cli.jar

Project: Apache Commons Collections
URL: http://commons.apache.org/proper/commons-collections/
LICENSE: Apache 2.0
FILES: dependencies/commons-collections-3.2.1-bin.tar.gz
JARS: commons-collections-3.2.1.jar

Project: Apache Commons BeanUtils
URL: http://commons.apache.org/proper/commons-beanutils/
LICENSE: Apache 2.0
FILES: dependencies/commons-beanutils-1.8.3-bin.tar.gz
JARS: commons-beanutils-1.8.3.jar

Project: Jackson JSON Core
URL: http://wiki.fasterxml.com/JacksonHome
License: Apache 2.0
FILES: jackson-core-2.2.0.jar jackson-databind-2.2.0.jar
JARS: jackson-core-2.2.0.jar jackson-databind-2.2.0.jar

