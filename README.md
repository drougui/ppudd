RDDLSim -- A simulator for the relational dynamic influence diagram language (RDDL).
====================================================================================

Copyright (C) 2010, Scott Sanner (ssanner@gmail.com) and Sungwook Yoon (sungwook.yoon@gmail.com)

LICENSE.txt:  GPLv3 license information for RDDLSim source and alternate license information for redistibuted 3rd party software

INSTALL.txt:  RDDLSim installation and execution instructions

PROTOCOL.txt: RDDLSim client/server protocol

PPUDD -- planning algorithm.
============================

Nicolas Drougard (nicolas.drougard@onera.fr)

- open this java project with eclipse;

- build path configuration: 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "Add External JARs" -> Select all .jar files of rddlsim/lib;

- if needed (for some imports in solvers): 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "JRE System Library" -> "Access rules" -> "Add..."
	and then 
	Resolution: "Accessible"
	Rule Pattern: "com/sun/org/**";
