all: generateRTGT	

generateRTGT: generateRTGT.o functions.o
	g++ -Wall -o generateRTGT generateRTGT.o functions.o

functions.o: functions.cpp
	g++ -Wall -c functions.cpp -I.

generateRTGT.o: generateRTGT.cpp
	g++ -Wall -c generateRTGT.cpp -I.

clean:
	rm generateRTGT.o functions.o

mrproper: clean
	rm generateRTGT

