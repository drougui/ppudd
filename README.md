PPUDD -- planning algorithm.
============================

Nicolas Drougard (nicolas.drougard@onera.fr)

## Synopsis

This project provides the code of the PPUDD solver of \pi-POMDPs described in
"Structured Possibilistic Planning Using Decision Diagrams", 
Nicolas Drougard, Florent Teichteil-Königsbuch, Jean-Loup Farges, Didier Dubois.

It provides also the experiment proposed in
"Qualitative Possibilistic Mixed-Observable MDPs" of the same authors,
and the International Probabilistic Planning Competition (IPPC) 2014 simulations.

This is presented in the context of the rddlsim project, 
and using a library (for ADDs in C++) from the prism project.

## Example

First terminal
```
  ./run rddl.competition.Server rddl/
```

Second terminal
```
export LD_LIBRARY_PATH=/path/to/the/directory/rddlsim/prism-4.0.3-src/lib/:$LD_LIBRARY_PATH
java -Xmx3g -Djava.library.path=/path/to/the/directory/rddlsim/prism-4.0.3-src/lib/ -cp '/path/to/the/directory/rddlsim/bin:/path/to/the/directory/rddlsim/lib/*' rddl.competition.Client /path/to/the/directory/rddlsim/input localhost ppudd rddl.policy.POPPUDD2policy 2323 42 RTGT_inst_pomdp.sperseus
```

## Installation

INSTALL rddlsim:
- open this java project with eclipse;

- build path configuration: 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "Add External JARs" -> Select all .jar files which are in rddlsim/lib;

- if needed (for some "import"s in solvers java files): 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "JRE System Library" -> "Access rules" -> "Add..."
	- Resolution: "Accessible" 
	- Rule Pattern: "com/sun/org/**";

INSTALL the CUDD library from the prism project to use ADDs:
-in the Makefile:
	```
	cd rddlsim/prism-4.0.3-src
	nano Makefile
	```
	-set the java directory
	for instance, for me : JAVA_DIR = /usr/lib/jvm/java-7-openjdk-amd64

	-and then 
```
make
```

- In Eclipse:
	go to the jdd directory in /src
	right click on JDDTest.java -> Run as -> Run Configurations... -> Environment tab -> New... 
	- Name: LD_LIBRARY_PATH 
	- Value: /path/to/the/directory/prism-4.0.3-src/lib/

and then run JDDTest.

## Reference

-RDDLSim -- A simulator for the relational dynamic influence diagram language (RDDL).
https://code.google.com/p/rddlsim/
Copyright (C) 2010, Scott Sanner (ssanner@gmail.com) and Sungwook Yoon (sungwook.yoon@gmail.com)

-prism: http://www.prismmodelchecker.org/

-CUDD: http://vlsi.colorado.edu/~fabio/CUDD/

-IPPC2014: https://cs.uwaterloo.ca/~mgrzes/IPPC_2014/

## License

GPL

