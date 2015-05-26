PPUDD -- planning algorithm.
============================

Nicolas Drougard (nicolas.drougard@onera.fr)

## Synopsis

This project provides the code of the solver of \pi-POMDPs PPUDD 
described in "Structured Possibilistic Planning Using Decision Diagrams", AAAI-14, 
Nicolas Drougard, Florent Teichteil-Königsbuch, Jean-Loup Farges and Didier Dubois.

It provides also the experiment proposed in
"Qualitative Possibilistic Mixed-Observable MDPs", UAI-13, of the same authors,
and the simulations of the International Probabilistic Planning Competition (IPPC) 2014.

This is presented in the context of the RDDLSim project, 
and using the CUDD library (for the use of ADDs in C++) 
from the prism project.

## Example

First terminal
```
  ./run rddl.competition.Server rddl/
```

Second terminal
```
export LD_LIBRARY_PATH=/path/to/the/directory/rddlsim/prism-4.0.3-src/lib/:$LD_LIBRARY_PATH
java -Xmx3g -Djava.library.path=/path/to/the/directory/rddlsim/prism-4.0.3-src/lib/ -cp '/path/to/the/directory/rddlsim/bin:/path/to/the/directory/rddlsim/lib/*' rddl.competition.Client /path/to/the/directory/rddlsim/ReachTheGoodTarget/input localhost ppudd rddl.policy.POPPUDD2policy 2323 42 RTGT_inst_pomdp.sperseus
```

## Installation

### INSTALL RDDLSim:
- open this java project with eclipse;

- build path configuration: 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "Add External JARs" -> Select all .jar files which are in rddlsim/lib;

- if needed (for some "import"s in solvers java files): 
	right click on the project -> "Build path" -> "Configure Build Path..." -> "Libraries" tab
	-> "JRE System Library" -> "Access rules" -> "Add..."
	- Resolution: "Accessible" 
	- Rule Pattern: "com/sun/org/**".

### INSTALL the CUDD library from the PRISM project (for ADDs):

```
cd rddlsim/prism-4.0.3-src
nano Makefile
```

- set the java directory, for instance, for me : JAVA_DIR = /usr/lib/jvm/java-7-openjdk-amd64;
- and then:

```
make
```

- In Eclipse: go to the jdd directory in /src;
	right click on JDDTest.java -> Run as -> Run Configurations... -> Environment tab -> New... 
	- Name: LD_LIBRARY_PATH 
	- Value: /path/to/the/directory/rddlsim/prism-4.0.3-src/lib/

and then run JDDTest.

## RTGT ("reach the good target" benchmark)
The file parameters.txt stores some parameters which may be modified.

- grid_size: size of a side of the grid;
- walls_number: number of walls in the grid (random positions stated);
- discount: discount factor of the resulting POMDP problem;
- DM, CM: parameters of the observation function.

```
cd ReachTheGoodTarget
make
./generateRTGT
```

To translate the returned rddl file RTGT_pomdp.rddl
into a spudd-sperseus one, use the following line:

```
java -Xmx3g -cp '/path/to/the/directory/rddlsim/bin:/path/to/the/directory/lib/*' rddl.translate.RDDL2Format input output spudd_sperseus
```

Finally:
- the input directory contains the generated .rddl file describing the POMDP problem;
- the output directory contains the translated .spudd_sperseus file describing it with trees (lisp-like format).

## Reference

- RDDLSim -- A simulator for the relational dynamic influence diagram language (RDDL).
https://code.google.com/p/rddlsim/
Copyright (C) 2010, Scott Sanner (ssanner@gmail.com) and Sungwook Yoon (sungwook.yoon@gmail.com)

- PRISM: http://www.prismmodelchecker.org/

- CUDD: http://vlsi.colorado.edu/~fabio/CUDD/

- IPPC-2014: https://cs.uwaterloo.ca/~mgrzes/IPPC_2014/

## License

GPL

